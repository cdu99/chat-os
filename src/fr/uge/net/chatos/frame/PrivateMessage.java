package fr.uge.net.chatos.frame;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class PrivateMessage implements Frame{
   private final String pseudo;
   private final String msg;
   private static final Charset UTF = StandardCharsets.UTF_8;

   public PrivateMessage(String pseudo, String msg) {
      this.pseudo=pseudo;
      this.msg=msg;
   }

   public String getPseudo() {
      return pseudo;
   }

   public String getMsg() {
      return msg;
   }

   @Override
   public ByteBuffer asByteBuffer() {
      var encodedMsg = UTF.encode(msg);
      var encPseudo = UTF.encode(pseudo);
      var bb = ByteBuffer.allocate(1 + Integer.BYTES*2 + encodedMsg.remaining()+encPseudo.remaining());
      bb.put((byte) 3);
      bb.putInt(encPseudo.remaining());
      bb.put(encPseudo);
      bb.putInt(encodedMsg.remaining());
      bb.put(encodedMsg);
      return bb;
   }
}
