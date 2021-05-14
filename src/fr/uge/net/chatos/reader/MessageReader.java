package fr.uge.net.chatos.reader;

import java.nio.ByteBuffer;

public class MessageReader implements Reader<Message> {

   private enum State {DONE, WAITING, ERROR}

   ;

   private final StringReader stringReader = new StringReader();
   private State state = State.WAITING;
   private Message value;
   private String pseudo;
   private String msg;
   private boolean gotPseudo = false;

   @Override
   public ProcessStatus process(ByteBuffer bb) {
      if (state == State.DONE || state == State.ERROR) {
         throw new IllegalStateException();
      }
      if (!gotPseudo) {
         switch (stringReader.process(bb)) {
            case ERROR:
               return ProcessStatus.ERROR;
            case REFILL:
               return ProcessStatus.REFILL;
            case DONE:
               pseudo = stringReader.get();
               gotPseudo = true;
               stringReader.reset();
         }
      }
      if (gotPseudo) {
         switch (stringReader.process(bb)) {
            case ERROR:
               return ProcessStatus.ERROR;
            case REFILL:
               return ProcessStatus.REFILL;
            case DONE:
               msg = stringReader.get();
               state = State.DONE;
               value = new Message(pseudo, msg);
               gotPseudo = false;
               return ProcessStatus.DONE;
         }
      }
      return ProcessStatus.ERROR;
   }

   @Override
   public Message get() {
      if (state != State.DONE) {
         throw new IllegalStateException();
      }
      return value;
   }

   @Override
   public void reset() {
      state = State.WAITING;
      stringReader.reset();
      value = null;
   }
}