package fr.uge.net.chatos.frame;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class ConnexionFrame implements Frame {
   private static final Charset UTF = StandardCharsets.UTF_8;
   private final String pseudo;
   public ConnexionFrame(String pseudo1) {
      this.pseudo = pseudo1;
   }

   @Override
   public ByteBuffer asByteBuffer() {
      var encPseudo = UTF.encode(pseudo);
      var bb = ByteBuffer.allocate(1 + Integer.BYTES + encPseudo.remaining());
      bb.put((byte) 1);
      bb.putInt(encPseudo.remaining());
      bb.put(encPseudo);
      return bb;
   }

   public String getPseudo() {
      return pseudo;
   }
}
