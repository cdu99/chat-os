package fr.uge.net.chatos.client;

import java.io.IOException;

public interface ClientContext {
   void doConnect() throws IOException;

   void doWrite() throws IOException;

   void doRead() throws IOException;
}
