package fr.uge.net.chatos.frame;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class LoginPrivate implements Frame {
   private final long connectId;
   private static final Charset UTF = StandardCharsets.UTF_8;

   public LoginPrivate(long connectId) {
      this.connectId = connectId;
   }

   public long getConnectId() {
      return connectId;
   }

   @Override
   public ByteBuffer asByteBuffer() {
      var bb = ByteBuffer.allocate(1 + + Long.BYTES);
      bb.put((byte) 9);
      bb.putLong(connectId);
      return bb;
   }
}
