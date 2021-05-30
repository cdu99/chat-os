package fr.uge.net.chatos.client;

import fr.uge.net.chatos.frame.PrivateConnexionAccept;
import fr.uge.net.chatos.frame.PrivateConnexionDecline;
import fr.uge.net.chatos.frame.PrivateConnexionRequest;
import fr.uge.net.chatos.frame.PrivateMessage;
import fr.uge.net.chatos.frame.SendingPublicMessage;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

public class TreatCommand {
   private final Map<String, ClientChatOs.PrivateContext> privateContextMap;
   private final ClientChatOs.MainContext mainContext;
   private static final Charset UTF = StandardCharsets.UTF_8;
   private final String pseudo;
   private final ClientChatOs ccos;
   private static final Logger logger = Logger.getLogger(TreatCommand.class.getName());

   public TreatCommand(Map<String, ClientChatOs.PrivateContext> privateContextMap, ClientChatOs.MainContext mainContext,
                       String pseudo, ClientChatOs ccos) {
      this.pseudo = pseudo;
      this.ccos = ccos;
      this.privateContextMap = privateContextMap;
      this.mainContext = mainContext;
   }

   /**
    * Parsing msg to process it:
    *
    * @param msg
    * @<pseudo> <message> to create a private message
    * <message> to create a message to all
    * /connect <pseudo> to make a private connexion request
    * /decline <pseudo> to decline it
    * /accept <pseudo> to accept it
    * /<pseudo> <message> to send message via a private tcp connexion to pseudo
    */
   public void parseCommand(String msg) {
      switch (msg.charAt(0)) {
         case '@':
            msg = msg.substring(1);
            var privateMessage = msg.split(" ", 2);
            var privateMsg = new PrivateMessage(privateMessage[0], privateMessage[1]);
            mainContext.queueMessage(privateMsg.asByteBuffer().flip());
            return;
         case '/':
            msg = msg.substring(1);
            var authentification = msg.split(" ", 2);
            if (authentification.length == 1) {
               // /<pseudo>
               // Pour fermer la connexion privée
               if (privateContextMap.containsKey(authentification[0])) {
                  if (privateContextMap.get(authentification[0]).state == ClientChatOs.State.ESTABLISHED) {
                     privateContextMap.get(authentification[0]).silentlyClose();
                     privateContextMap.remove(authentification[0]);
                     System.out.println("Connexion closed");
                  }
               } else {
                  System.out.println("No private connexion established with: " + authentification[0]);
               }
               return;
            }
            // /accept <pseudo>
            // Accepter la demande de connexion
            if (authentification[0].equals("accept")) {
               if (privateContextMap.containsKey(authentification[1])) {
                  if (privateContextMap.get(authentification[1]).getState() != ClientChatOs.State.PENDING_TARGET) {
                     System.out.println("No private connexion request from: " + authentification[1]);
                     return;
                  }
                  var acceptRequest = new PrivateConnexionAccept(authentification[1], pseudo);
                  mainContext.queueMessage(acceptRequest.asByteBuffer().flip());
                  return;
               } else {
                  System.out.println("No private connexion request from: " + authentification[1]);
               }
               return;
            }
            // /decline <pseudo>
            // Refuser
            else if (authentification[0].equals("decline")) {
               if (privateContextMap.containsKey(authentification[1])) {
                  if (privateContextMap.get(authentification[1]).getState() != ClientChatOs.State.PENDING_TARGET) {
                     System.out.println("No private connexion request from: " + authentification[1]);
                     return;
                  }
                  var declineRequest = new PrivateConnexionDecline(authentification[1], pseudo);
                  mainContext.queueMessage(declineRequest.asByteBuffer().flip());
                  privateContextMap.remove(authentification[1]);
                  return;
               } else {
                  System.out.println("No private connexion request from: " + authentification[1]);
               }

            } else if (authentification[0].equals("connect")) {
               // /connect <pseudo>
               // Faire une demande de connexion
               if (pseudo.equals(authentification[1])) {
                  System.out.println("Can't create private connexion with yourself");
                  return;
               }
               var request = new PrivateConnexionRequest(pseudo, authentification[1]);
               privateContextMap.put(authentification[1], new ClientChatOs.PrivateContext(ClientChatOs.State.PENDING_REQUESTER, ccos));
               mainContext.queueMessage(request.asByteBuffer().flip());
               return;
            }
            // /<pseudo> <line>
            // Envoyer line à pseudo via la connexion privée
            else {
               if (privateContextMap.containsKey(authentification[0])) {
                  var line = UTF.encode(authentification[1]);
                  var bb = ByteBuffer.allocate(Integer.BYTES + line.remaining());
                  bb.putInt(line.remaining()).put(line);
                  privateContextMap.get(authentification[0]).queueMessage(bb.flip());
               } else {
                  System.out.println("No private connexion established with: " + authentification[0]);
               }
            }
            return;
         default:
            // Message to all
            var publicMessage = new SendingPublicMessage(msg);
            mainContext.queueMessage(publicMessage.asByteBuffer().flip());
      }
   }
}
