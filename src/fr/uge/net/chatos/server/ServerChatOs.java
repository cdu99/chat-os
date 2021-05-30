package fr.uge.net.chatos.server;

import fr.uge.net.chatos.frame.ConnexionFrame;
import fr.uge.net.chatos.frame.Frame;
import fr.uge.net.chatos.frame.PrivateConnexionRequest;
import fr.uge.net.chatos.frame.PrivateMessage;
import fr.uge.net.chatos.frame.SendingPublicMessage;
import fr.uge.net.chatos.reader.FrameReader;
import fr.uge.net.chatos.reader.Message;
import fr.uge.net.chatos.reader.MessageReader;
import fr.uge.net.chatos.reader.StringReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerChatOs {

   private static final int BUFFER_SIZE = 1_024;
   private static final Logger logger = Logger.getLogger(ServerChatOs.class.getName());
   private final ServerSocketChannel serverSocketChannel;
   private final Selector selector;
   private final HashMap<String, SelectionKey> clients;
   private final HashMap<Long, PrivateTCPSession> privateSessions;

   public ServerChatOs(int port) throws IOException {
      serverSocketChannel = ServerSocketChannel.open();
      serverSocketChannel.bind(new InetSocketAddress(port));
      selector = Selector.open();
      clients = new HashMap<>();
      privateSessions = new HashMap<>();
   }

   public void launch() throws IOException {
      serverSocketChannel.configureBlocking(false);
      serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
      while (!Thread.interrupted()) {
         printKeys();
         System.out.println("Starting select");
         try {
            selector.select(this::treatKey);
         } catch (UncheckedIOException tunneled) {
            throw tunneled.getCause();
         }
         System.out.println("Select finished");
      }
   }

   private void treatKey(SelectionKey key) {
      printSelectedKey(key);
      try {
         if (key.isValid() && key.isAcceptable()) {
            doAccept(key);
         }
      } catch (IOException ioe) {
         throw new UncheckedIOException(ioe);
      }
      try {
         if (key.isValid() && key.isWritable()) {
            ((Context) key.attachment()).doWrite();
         }
         if (key.isValid() && key.isReadable()) {
            var context = ((Context) key.attachment());
            if (context.pseudo == null) {
               context.doRead();
               if (context.isPrivate) {
                  return;
               }
               if (context.pseudo == null || clients.containsKey(context.pseudo)) {
                  logger.info("Login error");
                  context.sendError(1);
                  context.doWrite();
                  silentlyClose(key);
                  return;
               }
               clients.put(context.pseudo, key);
            } else {
               context.doRead();
            }
         }
      } catch (IOException e) {
         logger.log(Level.INFO, "Connection closed with client due to IOException", e);
         silentlyClose(key);
      }
   }

   private void doAccept(SelectionKey key) throws IOException {
      var ssc = (ServerSocketChannel) key.channel();
      var sc = ssc.accept();
      if (sc == null) {
         return;
      }
      sc.configureBlocking(false);
      var clientKey = sc.register(selector, SelectionKey.OP_READ);
      clientKey.attach(new Context(this, clientKey));
   }

   // Message to all
   private void broadcast(Message message) {
      var keys = selector.keys();
      for (var key : keys) {
         if (key.attachment() != null) {
            var context = (Context) key.attachment();
            if (context.pseudo != null) {
               message.setOpcode(4);
               context.queueMessage(message);
            }
         }
      }
   }

   // Private message @
   private boolean privateMessage(String sender, String receiver, String msg) {
      var receiverKey = clients.get(receiver);
      if (receiverKey == null) {
         return false;
      }
      var context = (Context) receiverKey.attachment();
      var message = new Message(sender, msg);
      message.setOpcode(3);
      context.queueMessage(message);
      return true;
   }

   private void silentlyClose(SelectionKey key) {
      Channel sc = key.channel();
      try {
         sc.close();
      } catch (IOException e) {
         // ignore exception
      }
   }

   private void removeClient(String pseudo) {
      clients.remove(pseudo);
   }

   private boolean requestPrivateConnexion(String requester, String target) {
      var targetKey = clients.get(target);
      if (targetKey == null) {
         return false;
      }
      var targetContext = (Context) targetKey.attachment();
      var request = new Message(requester, target);
      request.setOpcode(5);
      targetContext.queueMessage(request);
      return true;
   }

   private boolean declinePrivateConnexion(String requester, String target) {
      var requesterKey = clients.get(requester);
      if (requesterKey == null) {
         return false;
      }
      var requesterContext = (Context) requesterKey.attachment();
      var request = new Message(requester, target);
      request.setOpcode(7);
      requesterContext.queueMessage(request);
      return true;
   }

   private boolean acceptPrivateConnexion(String requester, String target) {
      var requesterKey = clients.get(requester);
      var targetKey = clients.get(target);
      if (requesterKey == null || targetKey == null) {
         return false;
      }
      var requesterContext = (Context) requesterKey.attachment();
      var targetContext = (Context) targetKey.attachment();
      var request = new Message(requester, target);
      request.setOpcode(8);
      var connectId = Math.abs(new Random().nextLong());

      privateSessions.put(connectId, new PrivateTCPSession());

      request.setConnectId(connectId);
      requesterContext.queueMessage(request);
      targetContext.queueMessage(request);
      return true;
   }

   public static void main(String[] args) throws NumberFormatException, IOException {
      if (args.length != 1) {
         usage();
         return;
      }
      new ServerChatOs(Integer.parseInt(args[0])).launch();
   }

   private static void usage() {
      System.out.println("Usage : ServerChatOs port");
   }

   /****************** CONTEXT ******************/

   private static class Context {

      private static final Charset UTF = StandardCharsets.UTF_8;
      private final SelectionKey key;
      private final SocketChannel sc;
      private final ServerChatOs server;
      private String pseudo;

      private PrivateTCPSession privateTCPSession;
      private boolean isPrivate;

      private final ByteBuffer bbin = ByteBuffer.allocate(BUFFER_SIZE);
      private final ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE);
      private final Queue<Message> queue = new LinkedList<>();
      private final MessageReader messageReader = new MessageReader();
      private final StringReader stringReader = new StringReader();
      private final FrameReader fr = new FrameReader();
      private boolean closed = false;

      private Context(ServerChatOs server, SelectionKey key) {
         this.key = key;
         this.sc = (SocketChannel) key.channel();
         this.server = server;
      }

      /**
       * Performs the read action on sc
       * <p>
       * The convention is that both buffers are in write-mode before the call
       * to doRead and after the call
       *
       * @throws IOException
       */

      public void doRead() throws IOException {
         if (sc.read(bbin) == -1) {
            logger.info("Input stream closed");
            closed = true;
            updateInterestOps();
            return;
         }
         processIn();
         updateInterestOps();
      }

      /**
       * Process the content of bbin
       * <p>
       * The convention is that bbin is in write-mode before the call
       * to process and after the call
       */

      private void processIn() throws IOException {
         for (; ; ) {
            var status = fr.process(bbin);
            switch (status) {
               case ERROR:
                  silentlyClose();
                  return;
               case REFILL:
                  return;
               case DONE:
                  Frame frame = fr.get();
                  fr.reset();
                  treatFrame(frame);
                  break;
            }
         }
//         if (isPrivate) {
//            privateTCPSession.redirect(sc, bbin);
//            bbin.compact();
//            return;
//         }
//
//         if (pseudo == null) {
//            bbin.flip();
//            var opcode = bbin.get();
//            // It's a private connexion
//            if (opcode == 9) {
//               isPrivate = true;
//               var id = bbin.getLong();
//               this.privateTCPSession = server.privateSessions.get(id);
//               if (privateTCPSession.getState() == PrivateTCPSession.State.PENDING) {
//                  privateTCPSession.setFirstClient(sc);
//               } else if (privateTCPSession.getState() == PrivateTCPSession.State.ONE_CONNECTED) {
//                  privateTCPSession.setSecondClient(sc);
//                  privateTCPSession.established();
//                  server.privateSessions.remove(id);
//               }
//               bbin.compact();
//               return;
//            }
//            if (opcode != 1) {
//               return;
//            }
//            bbin.compact();
//            for (; ; ) {
//               var status = stringReader.process(bbin);
//               switch (status) {
//                  case DONE:
//                     pseudo = stringReader.get();
//                     stringReader.reset();
//                     break;
//                  case REFILL:
//                     return;
//                  case ERROR:
//                     silentlyClose();
//                     return;
//               }
//            }
//         } else {
//            bbin.flip();
//            switch (bbin.get()) {
//               case 2:
//                  bbin.compact();
//                  for (; ; ) {
//                     switch (stringReader.process(bbin)) {
//                        case DONE:
//                           var msg = stringReader.get();
//                           server.broadcast(new Message(pseudo, msg));
//                           stringReader.reset();
//                           break;
//                        case REFILL:
//                           return;
//                        case ERROR:
//                           silentlyClose();
//                           return;
//                     }
//                  }
//               case 3:
//                  bbin.compact();
//                  for (; ; ) {
//                     switch (messageReader.process(bbin)) {
//                        case DONE:
//                           var message = messageReader.get();
//                           var receiver = message.getPseudo();
//                           var msg = message.getMsg();
//                           var isReceiverPresent = server.privateMessage(pseudo, receiver, msg);
//                           messageReader.reset();
//                           if (!isReceiverPresent) {
//                              sendError(2);
//                           }
//                           break;
//                        case REFILL:
//                           return;
//                        case ERROR:
//                           silentlyClose();
//                           return;
//                     }
//                  }
//               case 5:
//                  bbin.compact();
//                  for (; ; ) {
//                     // (5) requester target
//                     // pseudo --> requester; msg --> target
//                     switch (messageReader.process(bbin)) {
//                        case DONE:
//                           var message = messageReader.get();
//                           if (!server.requestPrivateConnexion(message.getPseudo(), message.getMsg())) {
//                              sendError(2);
//                           }
//                           messageReader.reset();
//                           break;
//                        case REFILL:
//                           return;
//                        case ERROR:
//                           silentlyClose();
//                           return;
//                     }
//                  }
//               case 7:
//                  bbin.compact();
//                  for (; ; ) {
//                     switch (messageReader.process(bbin)) {
//                        case DONE:
//                           var message = messageReader.get();
//                           if (!server.declinePrivateConnexion(message.getPseudo(), message.getMsg())) {
//                              sendError(2);
//                           }
//                           messageReader.reset();
//                           break;
//                        case REFILL:
//                           return;
//                        case ERROR:
//                           silentlyClose();
//                           return;
//                     }
//                  }
//               case 6:
//                  bbin.compact();
//                  for (; ; ) {
//                     switch (messageReader.process(bbin)) {
//                        case DONE:
//                           var message = messageReader.get();
//                           if (!server.acceptPrivateConnexion(message.getPseudo(), message.getMsg())) {
//                              sendError(2);
//                           }
//                           messageReader.reset();
//                           break;
//                        case REFILL:
//                           return;
//                        case ERROR:
//                           silentlyClose();
//                           return;
//                     }
//                  }
//               default:
//                  logger.info("Unrecognized opcode");
//                  sendError(0);
//                  silentlyClose();
//                  return;
//            }
      }

      private void treatFrame(Frame frame) {
         if (frame instanceof ConnexionFrame) {
            var cf = (ConnexionFrame) frame;
            pseudo = cf.getPseudo();
         } else if (frame instanceof SendingPublicMessage) {
            var spm = (SendingPublicMessage) frame;
            server.broadcast(new Message(pseudo, spm.getMsg()));
         } else if (frame instanceof PrivateMessage) {
            var pm = (PrivateMessage) frame;
            var isReceiverPresent = server.privateMessage(pseudo, pm.getPseudo(), pm.getMsg());
            if (!isReceiverPresent) {
               sendError(2);
            }
         } else if (frame instanceof PrivateConnexionRequest) {
            logger.info("on arrive la");
            var pcr = (PrivateConnexionRequest) frame;
            if (!server.requestPrivateConnexion(pcr.getRequester(), pcr.getReceiver())) {
               sendError(2);
            }
         }
      }

      /**
       * Add a message to the message queue, tries to fill bbOut and updateInterestOps
       *
       * @param msg
       */

      private void queueMessage(Message msg) {
         queue.add(msg);
         processOut();
         updateInterestOps();
      }

      /**
       * Try to fill bbout from the message queue
       */

      private void processOut() {
         while (!queue.isEmpty() && bbout.hasRemaining()) {
            var message = queue.remove();
            var pseudo = UTF.encode(message.getPseudo());
            var msg = UTF.encode(message.getMsg());

            if (message.getConnectId() != -1) {
               if (bbout.remaining() > 1 + (Integer.BYTES * 2) + pseudo.remaining() + msg.remaining() + Long.BYTES) {
                  bbout.put((byte) message.getOpcode()).putInt(pseudo.remaining()).put(pseudo).putInt(msg.remaining())
                        .put(msg).putLong(message.getConnectId());
               } else {
                  queue.add(message);
               }
            } else {
               if (bbout.remaining() > 1 + (Integer.BYTES * 2) + pseudo.remaining() + msg.remaining()) {
                  bbout.put((byte) message.getOpcode()).putInt(pseudo.remaining()).put(pseudo).putInt(msg.remaining()).put(msg);
               } else {
                  queue.add(message);
               }
            }
         }
      }

      /**
       * Update the interestOps of the key looking
       * only at values of the boolean closed and
       * of both ByteBuffers.
       * <p>
       * The convention is that both buffers are in write-mode before the call
       * to updateInterestOps and after the call.
       * Also it is assumed that process has been be called just
       * before updateInterestOps.
       */

      private void updateInterestOps() {
         var newInterestOp = 0;
         if (bbin.hasRemaining() && !closed) {
            newInterestOp |= SelectionKey.OP_READ;
         }
         if (bbout.position() > 0) {
            newInterestOp |= SelectionKey.OP_WRITE;
         }
         if (newInterestOp == 0) {
            silentlyClose();
            return;
         } else {
            key.interestOps(newInterestOp);
         }
      }

      private void silentlyClose() {
         try {
            sc.close();
            server.removeClient(pseudo);
         } catch (IOException e) {
            // ignore exception
         }
      }

      /**
       * Performs the write action on sc
       * <p>
       * The convention is that both buffers are in write-mode before the call
       * to doWrite and after the call
       *
       * @throws IOException
       */

      private void doWrite() throws IOException {
         bbout.flip();
         sc.write(bbout);
         bbout.compact();
         processOut();
         updateInterestOps();
      }

      public void sendError(int errorCode) {
         if (bbout.remaining() > 1 + Integer.BYTES) {
            bbout.put((byte) 0).putInt(errorCode);
         }
         updateInterestOps();
      }
   }

   /********** Theses methods are here to help understanding the behavior of the selector **********/

   private String interestOpsToString(SelectionKey key) {
      if (!key.isValid()) {
         return "CANCELLED";
      }
      int interestOps = key.interestOps();
      ArrayList<String> list = new ArrayList<>();
      if ((interestOps & SelectionKey.OP_ACCEPT) != 0) list.add("OP_ACCEPT");
      if ((interestOps & SelectionKey.OP_READ) != 0) list.add("OP_READ");
      if ((interestOps & SelectionKey.OP_WRITE) != 0) list.add("OP_WRITE");
      return String.join("|", list);
   }

   public void printKeys() {
      Set<SelectionKey> selectionKeySet = selector.keys();
      if (selectionKeySet.isEmpty()) {
         System.out.println("The selector contains no key : this should not happen!");
         return;
      }
      System.out.println("The selector contains:");
      for (SelectionKey key : selectionKeySet) {
         SelectableChannel channel = key.channel();
         if (channel instanceof ServerSocketChannel) {
            System.out.println("\tKey for ServerSocketChannel : " + interestOpsToString(key));
         } else {
            SocketChannel sc = (SocketChannel) channel;
            System.out.println("\tKey for Client " + remoteAddressToString(sc) + " : " + interestOpsToString(key));
         }
      }
   }

   private String remoteAddressToString(SocketChannel sc) {
      try {
         return sc.getRemoteAddress().toString();
      } catch (IOException e) {
         return "???";
      }
   }

   public void printSelectedKey(SelectionKey key) {
      SelectableChannel channel = key.channel();
      if (channel instanceof ServerSocketChannel) {
         System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
      } else {
         SocketChannel sc = (SocketChannel) channel;
         System.out.println("\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
      }
   }

   private String possibleActionsToString(SelectionKey key) {
      if (!key.isValid()) {
         return "CANCELLED";
      }
      ArrayList<String> list = new ArrayList<>();
      if (key.isAcceptable()) list.add("ACCEPT");
      if (key.isReadable()) list.add("READ");
      if (key.isWritable()) list.add("WRITE");
      return String.join(" and ", list);
   }
}