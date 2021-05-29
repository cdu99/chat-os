package fr.uge.net.chatos.frame;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class SendingPublicMessage implements Frame {
   private final String msg;
   private static final Charset UTF = StandardCharsets.UTF_8;

   public SendingPublicMessage(String msg) {
      this.msg = msg;
   }

   @Override
   public ByteBuffer asByteBuffer() {
      var encodedMsg = UTF.encode(msg);
      var bb = ByteBuffer.allocate(1 + Integer.BYTES + encodedMsg.remaining());
      bb.put((byte) 2);
      bb.putInt(encodedMsg.remaining());
      bb.put(encodedMsg);
      return bb;
   }

   public String getMsg() {
      return msg;
   }
}
