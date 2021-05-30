package fr.uge.net.chatos.frame;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class IdPrivateFrame implements Frame {
   private final String requester;
   private final String receiver;
   private final long connectId;
   private static final Charset UTF = StandardCharsets.UTF_8;

   public IdPrivateFrame(String requester, String receiver, long connectId) {
      this.requester = requester;
      this.receiver = receiver;
      this.connectId = connectId;
   }

   public String getRequester() {
      return requester;
   }

   public String getReceiver() {
      return receiver;
   }

   public long getConnectId() {
      return connectId;
   }

   @Override
   public ByteBuffer asByteBuffer() {
      var encReq = UTF.encode(requester);
      var encRec = UTF.encode(receiver);
      var bb = ByteBuffer.allocate(1 + Integer.BYTES * 2 + encReq.remaining() + encRec.remaining() + Long.BYTES);
      bb.put((byte) 8);
      bb.putInt(encReq.remaining());
      bb.put(encReq);
      bb.putInt(encRec.remaining());
      bb.put(encRec);
      bb.putLong(connectId);
      return bb;
   }
}
