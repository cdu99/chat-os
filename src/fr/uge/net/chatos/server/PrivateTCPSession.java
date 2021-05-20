package fr.uge.net.chatos.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class PrivateTCPSession {
   private SocketChannel sc1;
   private SocketChannel sc2;

   public void established() throws IOException {
      var bb1= ByteBuffer.allocate(1);
      var bb2= ByteBuffer.allocate(1);
      bb1.put((byte) 10);
      bb2.put((byte) 10);
      sc1.write(bb1.flip());
      sc2.write(bb2.flip());
   }

   public enum State {PENDING, ONE_CONNECTED, BOTH_CONNECTED}

   ;
   private State state;

   public PrivateTCPSession() {
      state = State.PENDING;
   }

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
