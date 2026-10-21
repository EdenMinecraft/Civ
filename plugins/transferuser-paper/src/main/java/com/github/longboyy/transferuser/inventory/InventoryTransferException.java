package com.github.longboyy.transferuser.inventory;

public class InventoryTransferException extends Exception {

    public InventoryTransferException(String message) {
        super(message);
    }

    public InventoryTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
