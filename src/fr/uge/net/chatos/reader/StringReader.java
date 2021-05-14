package fr.uge.net.chatos.reader;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class StringReader implements Reader<String> {

   private enum State {DONE, WAITING, ERROR}

   ;

   private static int BUFFER_SIZE = 1_024;
   private static final Logger logger = Logger.getLogger(StringReader.class.getName());
   private static final Charset UTF = StandardCharsets.UTF_8;
   private final IntReader intReader = new IntReader();
   private final ByteBuffer internalbb = ByteBuffer.allocate(BUFFER_SIZE); // write-mode
   private State state = State.WAITING;
   private String value;
   private boolean gotSize = false;

   @Override
   public ProcessStatus process(ByteBuffer bb) {
      if (state == State.DONE || state == State.ERROR) {
         throw new IllegalStateException();
      }
      if (!gotSize) {
         switch (intReader.process(bb)) {
            case ERROR:
               return ProcessStatus.ERROR;
            case REFILL:
               return ProcessStatus.REFILL;
            case DONE:
               if (intReader.get() <= 0) {
                  return ProcessStatus.ERROR;
               }
               internalbb.limit(intReader.get());
               gotSize = true;
               break;
         }
      }
      bb.flip();
      try {
         if (bb.remaining() <= internalbb.remaining()) {
            internalbb.put(bb);
         } else {
            var oldLimit = bb.limit();
            bb.limit(internalbb.remaining());
            internalbb.put(bb);
            bb.limit(oldLimit);
         }
      } finally {
         bb.compact();
      }
      if (internalbb.position() < intReader.get()) {
         return ProcessStatus.REFILL;
      }
      state = State.DONE;
      internalbb.flip();
      value = UTF.decode(internalbb).toString();
      return ProcessStatus.DONE;
   }

   @Override
   public String get() {
      if (state != State.DONE) {
         throw new IllegalStateException();
      }
      return value;
   }

   @Override
   public void reset() {
      state = State.WAITING;
      intReader.reset();
      internalbb.clear();
      value = null;
      gotSize = false;
   }
}