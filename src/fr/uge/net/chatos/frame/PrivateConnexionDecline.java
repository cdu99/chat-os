package fr.uge.net.chatos.frame;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class PrivateConnexionDecline implements Frame{
   private final String requester;
   private final String receiver;
   private static final Charset UTF = StandardCharsets.UTF_8;

   public PrivateConnexionDecline(String requester, String receiver) {
      this.requester=requester;
      this.receiver=receiver;
   }

   public String getRequester() {
      return requester;
   }

   public String getReceiver() {
      return receiver;
   }

   @Override
   public ByteBuffer asByteBuffer() {
      var encReq = UTF.encode(requester);
      var encRec = UTF.encode(receiver);
      var bb = ByteBuffer.allocate(1 + Integer.BYTES*2 + encReq.remaining()+encRec.remaining());
      bb.put((byte) 7);
      bb.putInt(encReq.remaining());
      bb.put(encReq);
      bb.putInt(encRec.remaining());
      bb.put(encRec);
      return bb;
   }
}
