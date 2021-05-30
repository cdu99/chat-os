package fr.uge.net.chatos.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

// TODO cdla merde
public class PrivateTCPSession {
   private SocketChannel sc1;
   private SocketChannel sc2;

   public PrivateTCPSession() {
      state = State.PENDING;
   }

   public void doRead() throws IOException {

   }

   // processIn
   // processOut
   // doWrite
   // QueueByte
   // up interdsn
   // silentekncer close

   public void established() throws IOException {
      var bb1= ByteBuffer.allocate(1);
      var bb2= ByteBuffer.allocate(1);
      bb1.put((byte) 10);
      bb2.put((byte) 10);
      sc1.write(bb1.flip());
      sc2.write(bb2.flip());
   }

   public void redirect(SocketChannel sc, ByteBuffer bbin) throws IOException {
      if (sc.equals(sc1)) {
         sc2.write(bbin.flip());
      } else {
         sc1.write(bbin.flip());
      }
   }

   public enum State {PENDING, ONE_CONNECTED, BOTH_CONNECTED}

   ;
   private State state;

   public void setFirstClient(SocketChannel sc) {
      sc1 = sc;
      state = State.ONE_CONNECTED;
   }

   public void setSecondClient(SocketChannel sc) {
      sc2 = sc;
      state = State.BOTH_CONNECTED;
   }

   public State getState() {
      return state;
   }
}
