package fr.uge.net.chatos.reader;

// TODO c'est dla merde
// Remove opcode and connectId
public class Message {
   private final String pseudo;
   private final String msg;
   private int opcode;
   private long connectId;

   public Message(String pseudo, String message) {
      this.pseudo = pseudo;
      this.msg = message;
      connectId = -1;
   }

   @Override
   public String toString() {
      return "Message{" +
            "pseudo='" + pseudo + '\'' +
            ", message='" + msg + '\'' +
            '}';
   }

   public int getOpcode() {
      return opcode;
   }

   public void setOpcode(int opcode) {
      this.opcode = opcode;
   }

   public String getPseudo() {
      return pseudo;
   }

   public String getMsg() {
      return msg;
   }

   public void setConnectId(long id) {
      this.connectId = id;
   }

   public long getConnectId() {
      return connectId;
   }
}
