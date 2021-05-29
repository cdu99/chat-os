package fr.uge.net.chatos.frame;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class ErrorFrame implements Frame{
   private final int code;
   private static final Charset UTF = StandardCharsets.UTF_8;

   public ErrorFrame(int code) {
      this.code=code;
   }

   public int getCode() {
      return code;
   }

   @Override
   public ByteBuffer asByteBuffer() {
      var bb = ByteBuffer.allocate(2);
      bb.put((byte) 0);
      bb.put((byte) code);
      return bb;
   }
}
