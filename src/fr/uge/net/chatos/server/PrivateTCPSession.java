package fr.uge.net.chatos.server;

import java.nio.channels.Selector;

public class PrivateTCPSession {
   private final long idConnect;
   private final Selector selector;
   public PrivateTCPSession(long idConnect, Selector selector) {
      this.idConnect=idConnect;
      this.selector=selector;
   }
}
