package fr.uge.net.chatos.client;

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
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientChatOs {

   private static final int BUFFER_SIZE = 10_000;
   private static final Logger logger = Logger.getLogger(ClientChatOs.class.getName());
   private static final Charset UTF = StandardCharsets.UTF_8;

   private final SocketChannel sc;
   private final Selector selector;
   private final InetSocketAddress serverAddress;
   private final String pseudo;
   private final Thread console;
   private final ArrayBlockingQueue<String> commandQueue = new ArrayBlockingQueue<>(10);
   private Context uniqueContext;

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
         switch (msg.charAt(0)) {
            case '@':
               // TODO
               return;
            case '/':
               // TODO
               return;
            default:
               // Message to all
               var bbMsg = UTF.encode(msg);
               var bbPseudo = UTF.encode(pseudo);
               var bb = ByteBuffer.allocate(1 + (Integer.BYTES * 2) + bbMsg.remaining() + bbPseudo.remaining());
               bb.put((byte) 2).putInt(bbPseudo.remaining()).put(bbPseudo).putInt(bbMsg.remaining()).put(bbMsg);
               uniqueContext.queueMessage(bb.flip());
         }
      }
   }

   public void launch() throws IOException {
      sc.configureBlocking(false);
      var key = sc.register(selector, SelectionKey.OP_CONNECT);
      uniqueContext = new Context(key, pseudo);
      key.attach(uniqueContext);
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
            uniqueContext.doConnect();
         }
         if (key.isValid() && key.isWritable()) {
            uniqueContext.doWrite();
         }
         if (key.isValid() && key.isReadable()) {
            uniqueContext.doRead();
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

   private static class Context {

      private final String pseudo;
      private final SelectionKey key;
      private final SocketChannel sc;
      private final ByteBuffer bbin = ByteBuffer.allocate(BUFFER_SIZE);
      private final ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE);
      private final Queue<ByteBuffer> queue = new LinkedList<>(); // buffers read-mode
      private final MessageReader messageReader = new MessageReader();
      private boolean closed = false;

      private Context(SelectionKey key, String pseudo) {
         this.key = key;
         this.sc = (SocketChannel) key.channel();
         this.pseudo = pseudo;
      }

      public void doConnect() throws IOException {
         if (!sc.finishConnect()) {
            return; // the selector gave a bad hint
         }

         // Sending pseudo
         var bbPseudo = UTF.encode(pseudo);
         var bb = ByteBuffer.allocate(1 + Integer.BYTES + bbPseudo.remaining());
         bb.put((byte) 1).putInt(bbPseudo.remaining()).put(bbPseudo);
         queueMessage(bb.flip());

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
      private void queueMessage(ByteBuffer bb) {
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

      private void doWrite() throws IOException {
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
      private void doRead() throws IOException {
         if (sc.read(bbin) == -1) {
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

      private void processIn() {
         bbin.flip();
         switch (bbin.get()) {
            case 2:
               bbin.compact();
               for (; ; ) {
                  switch (messageReader.process(bbin)) {
                     case DONE:
                        var message = messageReader.get();
                        System.out.println(message.getPseudo() + ": " + message.getMsg());
                        messageReader.reset();
                        break;
                     case REFILL:
                        return;
                     case ERROR:
                        closed = true;
                        return;
                  }
               }
            case 0:
               treatError(bbin.getInt());
               bbin.compact();
               break;
            default:
               // TODO Trame erreur
               return;
         }

      }

      private void treatError(int errorCode) {
         if (errorCode == 1) {
            System.out.println("Login already used by another client");
            silentlyClose();
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
}
