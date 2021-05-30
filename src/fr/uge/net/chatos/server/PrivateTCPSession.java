package fr.uge.net.chatos.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class PrivateTCPSession {
   private SocketChannel sc1;
   private SocketChannel sc2;
   private ByteBuffer bb1 = ByteBuffer.allocate(1_024);
   private ByteBuffer bb2 = ByteBuffer.allocate(1_024);

   public PrivateTCPSession() {
      state = State.PENDING;
   }

   public void established() throws IOException {
      var bb1= ByteBuffer.allocate(1);
      var bb2= ByteBuffer.allocate(1);
      bb1.put((byte) 10);
      bb2.put((byte) 10);
      sc1.write(bb1.flip());
      sc2.write(bb2.flip());
   }

   public void redirect(SocketChannel sc, ByteBuffer bbin) throws IOException {
      bbin.flip();
      if (sc.equals(sc1)) {
         while (true) {
            if (bb1.remaining() >= bbin.remaining()) {
               bb1.put(bbin);
               bb1.flip();
               sc2.write(bb1);
               bb1.compact();
               break;
            } else {
               var buff2 = ByteBuffer.allocate(bb1.capacity() * 2);
               buff2.put(bb1);
               bb1 = buff2;
            }
         }
      } else {
         while (true) {
            if (bb2.remaining() >= bbin.remaining()) {
               bb2.put(bbin);
               bb2.flip();
               sc1.write(bb2);
               bb2.compact();
               break;
            } else {
               var buff2 = ByteBuffer.allocate(bb1.capacity() * 2);
               buff2.put(bb2);
               bb2 = buff2;
            }
         }
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
