package fr.uge.net.chatos.reader;

import java.nio.ByteBuffer;

public class IdPrivateReader implements Reader<IdPrivate> {

   private enum State {DONE, WAITING, ERROR}

   ;

   private final MessageReader messageReader = new MessageReader();
   private final LongReader longReader = new LongReader();
   private State state = State.WAITING;
   private long id;
   private IdPrivate value;
   private Message message;
   private boolean gotMessage = false;

   @Override
   public ProcessStatus process(ByteBuffer bb) {
      if (state == State.DONE || state == State.ERROR) {
         throw new IllegalStateException();
      }
      if (!gotMessage) {
         switch (messageReader.process(bb)) {
            case ERROR:
               return ProcessStatus.ERROR;
            case REFILL:
               return ProcessStatus.REFILL;
            case DONE:
               message = messageReader.get();
               gotMessage = true;
               messageReader.reset();
         }
      }
      if (gotMessage) {
         switch (longReader.process(bb)) {
            case ERROR:
               return ProcessStatus.ERROR;
            case REFILL:
               return ProcessStatus.REFILL;
            case DONE:
               id = longReader.get();
               state = State.DONE;
               value = new IdPrivate(message.getPseudo(), message.getMsg(), id);
               gotMessage = false;
               longReader.reset();
               return ProcessStatus.DONE;
         }
      }
      return ProcessStatus.ERROR;
   }

   @Override
   public IdPrivate get() {
      if (state != State.DONE) {
         throw new IllegalStateException();
      }
      return value;
   }

   @Override
   public void reset() {
      gotMessage = false;
      state = State.WAITING;
      messageReader.reset();
      longReader.reset();
      value = null;
      message = null;
   }
}