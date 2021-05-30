package fr.uge.net.chatos.client;

import fr.uge.net.chatos.frame.ConnexionFrame;
import fr.uge.net.chatos.frame.ErrorFrame;
import fr.uge.net.chatos.frame.Frame;
import fr.uge.net.chatos.frame.PrivateConnexionRequest;
import fr.uge.net.chatos.frame.PrivateMessage;
import fr.uge.net.chatos.frame.PublicMessage;
import fr.uge.net.chatos.reader.FrameReader;
import fr.uge.net.chatos.reader.IdPrivateReader;
import fr.uge.net.chatos.reader.MessageReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientChatOs {
   public enum State {PENDING_TARGET, PENDING_REQUESTER, ESTABLISHED, CLOSED}

   private static final int BUFFER_SIZE = 10_000;
   private static final Logger logger = Logger.getLogger(ClientChatOs.class.getName());
   private static final Charset UTF = StandardCharsets.UTF_8;

   private final SocketChannel sc;
   private final Selector selector;
   private final InetSocketAddress serverAddress;
   private final String pseudo;
   private final Thread console;
   private final ArrayBlockingQueue<String> commandQueue = new ArrayBlockingQueue<>(10);
   private MainContext mainContext;
   private final Map<String, PrivateContext> privateContextMap = new HashMap<>();
   private TreatCommand treatCommand;

   public ClientChatOs(String pseudo, InetSocketAddress serverAddress) throws IOException {
      this.serverAddress = serverAddress;
      this.pseudo = pseudo;
      this.sc = SocketChannel.open();
      this.selector = Selector.open();
      this.console = new Thread(this::consoleRun);
   }

   private void consoleRun() {
      try {
         var scan = new Scanner(System.in);
         while (scan.hasNextLine()) {
            var msg = scan.nextLine();
            sendCommand(msg);
         }
      } catch (InterruptedException e) {
         logger.info("Console thread has been interrupted");
      } finally {
         logger.info("Console thread stopping");
      }
   }

   /**
    * Send a command to the selector via commandQueue and wake it up
    *
    * @param msg
    * @throws InterruptedException
    */

   private void sendCommand(String msg) throws InterruptedException {
      commandQueue.put(msg);
      selector.wakeup();
   }

   /**
    * Processes the command from commandQueue
    */

   private void processCommands() {
      while (!commandQueue.isEmpty()) {
         var msg = commandQueue.remove();
         treatCommand.parseCommand(msg);
      }
   }

   public void launch() throws IOException {
      sc.configureBlocking(false);
      var key = sc.register(selector, SelectionKey.OP_CONNECT);
      mainContext = new MainContext(key, pseudo, this);
      key.attach(mainContext);
      treatCommand = new TreatCommand(privateContextMap, mainContext, pseudo, this);
      sc.connect(serverAddress);

      console.start();

      while (!Thread.interrupted()) {
         try {
            selector.select(this::treatKey);
            processCommands();
         } catch (UncheckedIOException tunneled) {
            throw tunneled.getCause();
         }
      }
   }

   private void treatKey(SelectionKey key) {
      try {
         if (key.isValid() && key.isConnectable()) {
            var ctx = (ClientContext) key.attachment();
            ctx.doConnect();
         }
         if (key.isValid() && key.isWritable()) {
            var ctx = (ClientContext) key.attachment();
            ctx.doWrite();
         }
         if (key.isValid() && key.isReadable()) {
            var ctx = (ClientContext) key.attachment();
            ctx.doRead();
         }
      } catch (IOException ioe) {
         throw new UncheckedIOException(ioe);
      }
   }

   private void silentlyClose(SelectionKey key) {
      Channel sc = (Channel) key.channel();
      try {
         sc.close();
      } catch (IOException e) {
         // ignore exception
      }
   }

   public static void main(String[] args) throws NumberFormatException, IOException {
      if (args.length != 3) {
         usage();
         return;
      }
      if (args[0].length() > 9) {
         System.out.println("Login name cannot exceed 9 caracters");
         return;
      }
      Pattern pattern = Pattern.compile("[^a-zA-Z0-9]");
      Matcher matcher = pattern.matcher(args[0]);
      if (matcher.find()) {
         System.out.println("Login cannot contains special characters");
         return;
      }
      new ClientChatOs(args[0], new InetSocketAddress(args[1], Integer.parseInt(args[2]))).launch();
   }

   private static void usage() {
      System.out.println("Usage : ClientChat login hostname port");
   }

   /****************** CONTEXT ******************/

   static class MainContext implements ClientContext {
      private final FrameReader fr = new FrameReader();
      private final ClientChatOs clientChatOs;
      private final String pseudo;
      private final SelectionKey key;
      private final SocketChannel sc;
      private final ByteBuffer bbin = ByteBuffer.allocate(BUFFER_SIZE);
      private final ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE);
      private final Queue<ByteBuffer> queue = new LinkedList<>(); // buffers read-mode
      private final MessageReader messageReader = new MessageReader();
      private final IdPrivateReader idPrivateReader = new IdPrivateReader();
      private boolean closed = false;

      private MainContext(SelectionKey key, String pseudo, ClientChatOs clientChatOs) {
         this.key = key;
         this.sc = (SocketChannel) key.channel();
         this.pseudo = pseudo;
         this.clientChatOs = clientChatOs;
      }

      public void doConnect() throws IOException {
         if (!sc.finishConnect()) {
            return; // the selector gave a bad hint
         }

         // Sending pseudo
         var newConnexio = new ConnexionFrame(pseudo);
         queueMessage(newConnexio.asByteBuffer().flip());

         updateInterestOps();
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
         var interesOps = 0;
         if (!closed && bbin.hasRemaining()) {
            interesOps = interesOps | SelectionKey.OP_READ;
         }
         if (bbout.position() != 0) {
            interesOps |= SelectionKey.OP_WRITE;
         }
         if (interesOps == 0) {
            silentlyClose();
            return;
         }
         key.interestOps(interesOps);
      }

      /**
       * Add a message to the message queue, tries to fill bbOut and updateInterestOps
       *
       * @param bb
       */
      public void queueMessage(ByteBuffer bb) {
         queue.add(bb);
         processOut();
         updateInterestOps();
      }

      /**
       * Try to fill bbout from the message queue
       */
      private void processOut() {
         while (!queue.isEmpty()) {
            var bb = queue.peek();
            if (bb.remaining() <= bbout.remaining()) {
               queue.remove();
               bbout.put(bb);
            } else {
               break;
            }
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

      public void doWrite() throws IOException {
         bbout.flip();
         sc.write(bbout);
         bbout.compact();
         processOut();
         updateInterestOps();
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
            closed = true;
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
//         bbin.flip();
//         switch (bbin.get()) {
//            case 2:
//               bbin.compact();
//               for (; ; ) {
//                  switch (messageReader.process(bbin)) {
//                     case DONE:
//                        var message = messageReader.get();
//                        System.out.println(message.getPseudo() + ": " + message.getMsg());
//                        messageReader.reset();
//                        break;
//                     case REFILL:
//                        return;
//                     case ERROR:
//                        closed = true;
//                        return;
//                  }
//               }
//            case 0:
//               treatError(bbin.getInt());
//               bbin.compact();
//               break;
//            case 3:
//               bbin.compact();
//               for (; ; ) {
//                  switch (messageReader.process(bbin)) {
//                     case DONE:
//                        var message = messageReader.get();
//                        System.out.println("Private message from " + message.getPseudo() + ": " + message.getMsg());
//                        messageReader.reset();
//                        break;
//                     case REFILL:
//                        return;
//                     case ERROR:
//                        closed = true;
//                        return;
//                  }
//               }
//            case 5:
//               bbin.compact();
//               for (; ; ) {
//                  switch (messageReader.process(bbin)) {
//                     case DONE:
//                        var message = messageReader.get();
//                        System.out.println("Private connexion request from: " + message.getPseudo() +
//                              " (/accept " + message.getPseudo() + " or /decline " + message.getPseudo() + ")");
//                        clientChatOs.privateContextMap.put(message.getPseudo(),
//                              new PrivateContext(State.PENDING_TARGET, clientChatOs));
//                        messageReader.reset();
//                        break;
//                     case REFILL:
//                        return;
//                     case ERROR:
//                        closed = true;
//                        return;
//                  }
//               }
//            case 7:
//               bbin.compact();
//               for (; ; ) {
//                  switch (messageReader.process(bbin)) {
//                     case DONE:
//                        var message = messageReader.get();
//                        System.out.println("Private connexion request with: " + message.getMsg() +
//                              " declined");
//                        clientChatOs.privateContextMap.remove(message.getMsg());
//                        messageReader.reset();
//                        break;
//                     case REFILL:
//                        return;
//                     case ERROR:
//                        closed = true;
//                        return;
//                  }
//               }
//            case 8:
//               bbin.compact();
//               for (; ; ) {
//                  switch (idPrivateReader.process(bbin)) {
//                     case DONE:
//                        var idPrivate = idPrivateReader.get();
//                        if (idPrivate.getRequester().equals(pseudo)) {
//                           clientChatOs.privateContextMap.get(idPrivate.getTarget())
//                                 .initializePrivateConnexion(idPrivate.getConnectId());
//                        } else {
//                           clientChatOs.privateContextMap.get(idPrivate.getRequester())
//                                 .initializePrivateConnexion(idPrivate.getConnectId());
//                        }
//                        idPrivateReader.reset();
//                        break;
//                     case REFILL:
//                        return;
//                     case ERROR:
//                        closed = true;
//                        return;
//                  }
//               }
//            default:
//               System.out.println("ERROR, DISCONNECTION");
//               silentlyClose();
//               return;
//         }

      }

      private void treatFrame(Frame frame) {
         if (frame instanceof PublicMessage) {
            var pm = (PublicMessage) frame;
            System.out.println(pm.getPseudo() + ": " + pm.getMsg());

         } else if (frame instanceof PrivateMessage) {
            var pm = (PrivateMessage) frame;
            System.out.println("Private message from " + pm.getPseudo() + ": " + pm.getMsg());
         } else if (frame instanceof ErrorFrame) {
            var ef = (ErrorFrame) frame;
            var efCode = ef.getCode();
            if (efCode == 1) {
               System.out.println("Login already used by another client");
               silentlyClose();
               closed = true;
               return;
            } else if (efCode == 2) {
               System.out.println("Receiver does not exist");
               return;
            }
         } else if (frame instanceof PrivateConnexionRequest) {
            var pcr = (PrivateConnexionRequest) frame;
            System.out.println("Private connexion request from: " + pcr.getRequester() +
                  " (/accept " + pcr.getRequester() + " or /decline " + pcr.getRequester() + ")");
            clientChatOs.privateContextMap.put(pcr.getRequester(),
                  new PrivateContext(State.PENDING_TARGET, clientChatOs));
         }

      }

      private void treatError(int errorCode) {
         switch (errorCode) {
            case 1:
               System.out.println("Login already used by another client");
               silentlyClose();
               closed = true;
               return;
            case 2:
               System.out.println("Receiver does not exist");
               return;
            case 0:
               silentlyClose();
               return;
         }
      }

      private void silentlyClose() {
         try {
            sc.close();
         } catch (IOException e) {
            // ignore exception
         }
      }
   }

   static class PrivateContext implements ClientContext {

      private SelectionKey key;
      private SocketChannel sc;
      private final ByteBuffer bbin = ByteBuffer.allocate(BUFFER_SIZE);
      private final ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE);
      private final Queue<ByteBuffer> queue = new LinkedList<>(); // buffers read-mode
      public State state;
      private long connectId;
      private final ClientChatOs clientChatOs;

      public PrivateContext(State state, ClientChatOs clientChatOs) {
         this.state = state;
         this.clientChatOs = clientChatOs;
      }

      public State getState() {
         return state;
      }

      @Override
      public void doConnect() throws IOException {
         if (!sc.finishConnect()) {
            return; // the selector gave a bad hint
         }

         // Sending login private
         var bb = ByteBuffer.allocate(1 + Long.BYTES);
         bb.put((byte) 9).putLong(connectId);
         queueMessage(bb.flip());

         updateInterestOps();
      }

      public void queueMessage(ByteBuffer bb) {
         queue.add(bb);
         processOut();
         updateInterestOps();
      }

      public void doWrite() throws IOException {
         bbout.flip();
         sc.write(bbout);
         bbout.compact();
         processOut();
         updateInterestOps();
      }

      private void processOut() {
         while (!queue.isEmpty()) {
            var bb = queue.peek();
            if (bb.remaining() <= bbout.remaining()) {
               queue.remove();
               bbout.put(bb);
            } else {
               break;
            }
         }
      }

      @Override
      public void doRead() throws IOException {
         if (sc.read(bbin) == -1) {
            state = State.CLOSED;
         }
         processIn();
         updateInterestOps();
      }

      private void processIn() {
         bbin.flip();
         if (state != State.ESTABLISHED) {
            if (bbin.get() != 10) {
               System.out.println("ERROR DISCONNECTION");
               silentlyClose();
               return;
            } else {
               System.out.println("Connexion established");
               state = State.ESTABLISHED;
            }
            bbin.compact();
         } else {
            var size = bbin.getInt();
            var bb = ByteBuffer.allocate(size);
            for (var i = 0; i < size; i++) {
               bb.put(bbin.get());
            }
            System.out.println(UTF.decode(bb.flip()).toString());
            bbin.compact();
         }
      }

      private void updateInterestOps() {
         var interesOps = 0;
         if (state != State.CLOSED && bbin.hasRemaining()) {
            interesOps = interesOps | SelectionKey.OP_READ;
         }
         if (bbout.position() != 0) {
            interesOps |= SelectionKey.OP_WRITE;
         }
         if (interesOps == 0) {
            silentlyClose();
            return;
         }
         key.interestOps(interesOps);
      }

      public void silentlyClose() {
         try {
            sc.close();
         } catch (IOException e) {
            // ignore exception
         }
      }

      public void initializePrivateConnexion(long connectId) throws IOException {
         this.connectId = connectId;
         sc = SocketChannel.open();
         sc.configureBlocking(false);

         sc.connect(clientChatOs.serverAddress);

         key = sc.register(clientChatOs.selector, SelectionKey.OP_CONNECT);
         key.attach(this);
      }
   }
}
