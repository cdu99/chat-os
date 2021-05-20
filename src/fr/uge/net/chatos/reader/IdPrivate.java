package fr.uge.net.chatos.reader;

public class IdPrivate {
   private final String requester;
   private final String target;
   private long connectId;

   public IdPrivate(String requester, String target, Long connectId) {
      this.requester = requester;
      this.target = target;
      this.connectId = connectId;
   }

   @Override
   public String toString() {
      return "IdPrivate{" +
            "requester='" + requester + '\'' +
            ", target='" + target + '\'' +
            ", connectId=" + connectId +
            '}';
   }

   public String getRequester() {
      return requester;
   }

   public String getTarget() {
      return target;
   }

   public long getConnectId() {
      return connectId;
   }
}
