package me.aleksilassila.litematica.printer.guide;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.aleksilassila.litematica.printer.printer.action.Action;

public record Result(Action action, boolean passToNext, boolean skipOtherGuide) {
   public static final Result PASS = new Result(null, true, false);
   public static final Result EMPTY = PASS;
   public static final Result SKIP = new Result(null, false, true);

   public static Result success(Action action) {
      return new Result(action, false, false);
   }

   public static Result success() {
      return new Result(null, false, false);
   }

   public static Result resultIf(boolean condition, Action action) {
      return condition ? success(action) : PASS;
   }

   public static Result resultIf(boolean condition, Supplier<Action> supplier) {
      return condition ? success(supplier.get()) : PASS;
   }

   public Optional<Action> toOptional() {
      return Optional.ofNullable(this.action);
   }

   public boolean hasAction() {
      return this.action != null;
   }

   public void ifHasAction(Consumer<Action> consumer) {
      if (this.action != null) {
         consumer.accept(this.action);
      }
   }

   public Result or(Result other) {
      return this.passToNext ? other : this;
   }

   public Result or(Supplier<Result> supplier) {
      return this.passToNext ? supplier.get() : this;
   }
}
