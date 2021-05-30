package fr.uge.net.chatos.reader;

public class Message {
   private final String pseudo;
   private final String msg;

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

   public String getPseudo() {
      return pseudo;
   }

   public String getMsg() {
      return msg;
   }
}
