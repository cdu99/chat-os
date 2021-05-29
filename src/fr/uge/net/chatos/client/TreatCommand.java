package fr.uge.net.chatos.client;

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

   public void parseCommand(String msg) {
      switch (msg.charAt(0)) {
         case '@':
            msg = msg.substring(1);
            var privateMessage = msg.split(" ", 2);
            var bbPrivateMsg = UTF.encode(privateMessage[1]);
            var bbPseudo = UTF.encode(privateMessage[0]);
            var bbPrivate = ByteBuffer.allocate(1 + (Integer.BYTES * 2) + bbPrivateMsg.remaining()
                  + bbPseudo.remaining());
            bbPrivate.put((byte) 3).putInt(bbPseudo.remaining()).put(bbPseudo)
                  .putInt(bbPrivateMsg.remaining()).put(bbPrivateMsg);
            mainContext.queueMessage(bbPrivate.flip());
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
                  var bbRequester = UTF.encode(authentification[1]);
                  var bbTarget = UTF.encode(pseudo);
                  var bbAcceptRequestPrivate = ByteBuffer.allocate(1 + (Integer.BYTES * 2) + bbRequester.remaining()
                        + bbTarget.remaining());
                  bbAcceptRequestPrivate.put((byte) 6).putInt(bbRequester.remaining()).put(bbRequester)
                        .putInt(bbTarget.remaining()).put(bbTarget);
                  mainContext.queueMessage(bbAcceptRequestPrivate.flip());
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
                  var bbTarget = UTF.encode(pseudo);
                  var bbRequester = UTF.encode(authentification[1]);
                  var bbDeclineRequestPrivate = ByteBuffer.allocate(1 + (Integer.BYTES * 2) +
                        bbRequester.remaining()
                        + bbTarget.remaining());
                  bbDeclineRequestPrivate.put((byte) 7).putInt(bbRequester.remaining()).put(bbRequester)
                        .putInt(bbTarget.remaining()).put(bbTarget);
                  mainContext.queueMessage(bbDeclineRequestPrivate.flip());
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
               var bbRequester = UTF.encode(pseudo);
               var bbTarget = UTF.encode(authentification[1]);
               var bbRequestPrivate = ByteBuffer.allocate(1 + (Integer.BYTES * 2) + bbRequester.remaining()
                     + bbTarget.remaining());
               bbRequestPrivate.put((byte) 5).putInt(bbRequester.remaining()).put(bbRequester)
                     .putInt(bbTarget.remaining()).put(bbTarget);
               privateContextMap.put(authentification[1], new ClientChatOs.PrivateContext(ClientChatOs.State.PENDING_REQUESTER, ccos));
               mainContext.queueMessage(bbRequestPrivate.flip());
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
