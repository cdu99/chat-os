package fr.uge.net.chatos.reader;

public class Message {
   private final String pseudo;
   private final String msg;
   private int opcode;

   public Message(String pseudo, String message) {
      this.pseudo = pseudo;
      this.msg = message;
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
}
