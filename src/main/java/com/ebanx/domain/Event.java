package com.ebanx.domain;

/**
 * All events the system understands. Sealed to a fixed set of records
 * so that anything that switches over an Event (see AccountService.apply)
 * is checked for exhaustiveness by the compiler: if a new event type is
 * ever added here, every switch that forgot to handle it stops compiling
 * instead of silently doing the wrong thing at runtime.
 */
public sealed interface Event permits Deposit, Withdraw, Transfer {
}
