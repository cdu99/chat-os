package fr.uge.net.chatos.reader;

import fr.uge.net.chatos.frame.ConnexionFrame;
import fr.uge.net.chatos.frame.ErrorFrame;
import fr.uge.net.chatos.frame.Frame;
import fr.uge.net.chatos.frame.PrivateMessage;
import fr.uge.net.chatos.frame.PublicMessage;
import fr.uge.net.chatos.frame.SendingPublicMessage;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

public class FrameReader implements Reader<Frame> {
   private static final Logger logger = Logger.getLogger(FrameReader.class.getName());


   private enum State {DONE, WAITING, ERROR}

   ;
   private State state;
   private int opcode;
   private boolean gotOpcode;
   private Frame value;
   private final StringReader stringReader = new StringReader();
   private final MessageReader messageReader = new MessageReader();
   private final IntReader intReader = new IntReader();

   @Override
   public ProcessStatus process(ByteBuffer bb) {
      if (state == State.DONE || state == State.ERROR) {
         throw new IllegalStateException();
      }
      bb.flip();
      if (!bb.hasRemaining()) {
         bb.compact();
         return ProcessStatus.REFILL;
      }
      if (!gotOpcode) {
         opcode = bb.get();
         gotOpcode = true;
      }
      if (gotOpcode) {
         bb.compact();
         switch (opcode) {
            case 1:
               // client is sending his login
               for (; ; ) {
                  var status = stringReader.process(bb);
                  switch (status) {
                     case DONE:
                        var pseudo = stringReader.get();
                        value = new ConnexionFrame(pseudo);
                        gotOpcode = false;
                        stringReader.reset();
                        state = State.DONE;
                        return ProcessStatus.DONE;
                     case REFILL:
                        return ProcessStatus.REFILL;
                     case ERROR:
                        return ProcessStatus.ERROR;
                  }
               }
            case 2:
               // A client is sending a msg to all
               switch (stringReader.process(bb)) {
                  case ERROR:
                     return ProcessStatus.ERROR;
                  case REFILL:
                     return ProcessStatus.REFILL;
                  case DONE:
                     var msg = stringReader.get();
                     state = State.DONE;
                     value = new SendingPublicMessage(msg);
                     gotOpcode = false;
                     stringReader.reset();
                     return ProcessStatus.DONE;
               }
            case 4:
               switch (messageReader.process(bb)) {
                  case ERROR:
                     return ProcessStatus.ERROR;
                  case REFILL:
                     return ProcessStatus.REFILL;
                  case DONE:
                     var message = messageReader.get();
                     state = State.DONE;
                     value = new PublicMessage(message.getPseudo(), message.getMsg());
                     gotOpcode = false;
                     messageReader.reset();
                     return ProcessStatus.DONE;
               }
               // a client is sending pm
            case 3:
               switch (messageReader.process(bb)) {
                  case ERROR:
                     return ProcessStatus.ERROR;
                  case REFILL:
                     return ProcessStatus.REFILL;
                  case DONE:
                     var message = messageReader.get();
                     state = State.DONE;
                     value = new PrivateMessage(message.getPseudo(), message.getMsg());
                     gotOpcode = false;
                     messageReader.reset();
                     return ProcessStatus.DONE;
               }
            case 0:
               switch (intReader.process(bb)){
               case ERROR:
                  return ProcessStatus.ERROR;
               case REFILL:
                  return ProcessStatus.REFILL;
               case DONE:
                  var code = intReader.get();
                  state = State.DONE;
                  value = new ErrorFrame(code);
                  gotOpcode = false;
                  intReader.reset();
                  return ProcessStatus.DONE;
            }
            default:
               return ProcessStatus.ERROR;
         }
      }
      return ProcessStatus.ERROR;
   }

   @Override
   public Frame get() {
      if (state != State.DONE) {
         throw new IllegalStateException();
      }
      return value;
   }

   @Override
   public void reset() {
      state = State.WAITING;
      // tous les readers reset
      stringReader.reset();
      intReader.reset();
      messageReader.reset();
      value = null;
   }
}
