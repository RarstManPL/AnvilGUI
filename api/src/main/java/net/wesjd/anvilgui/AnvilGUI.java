package net.wesjd.anvilgui;

import net.wesjd.anvilgui.version.VersionMatcher;
import net.wesjd.anvilgui.version.VersionWrapper;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * An anvil gui, used for gathering a user's input
 *
 * @author Wesley Smith
 * @since 1.0
 */
public class AnvilGUI {

    /**
     * The local {@link VersionWrapper} object for the server's version
     */
    private static VersionWrapper WRAPPER = new VersionMatcher().match();

    /**
     * The {@link Plugin} that this anvil GUI is associated with
     */
    private final Plugin plugin;
    /**
     * The player who has the GUI open
     */
    private final Player player;
    /**
     * The title of the anvil inventory
     */
    private String inventoryTitle;
    /**
     * The ItemStack that is in the {@link Slot#INPUT_LEFT} slot.
     */
    private Map<Integer, ItemStack> items;
    /**
     * A state that decides where the anvil GUI is able to be closed by the user
     */
    private final boolean preventClose;
    /**
     * An {@link Consumer} that is called when the anvil GUI is closed
     */
    private final Consumer<InventoryCloseEvent> closeListener;
    /**
     * An {@link BiFunction} that is called when the {@link Slot#OUTPUT} slot has been clicked
     */
    private final BiFunction<Player, String, String> completeFunction;

    /**
     * The container id of the inventory, used for NMS methods
     */
    private int containerId;
    /**
     * The inventory that is used on the Bukkit side of things
     */
    private Inventory inventory;
    /**
     * The listener holder class
     */
    private final ListenUp listener = new ListenUp();

    /**
     * Represents the state of the inventory being open
     */
    private boolean open;

    /**
     * Create an AnvilGUI and open it for the player.
     *
     * @param plugin     A {@link org.bukkit.plugin.java.JavaPlugin} instance
     * @param holder     The {@link Player} to open the inventory for
     * @param insert     What to have the text already set to
     * @param biFunction A {@link BiFunction} that is called when the player clicks the {@link Slot#OUTPUT} slot
     * @throws NullPointerException If the server version isn't supported
     * @deprecated As of version 1.2.3, use {@link AnvilGUI.Builder}
     */
    @Deprecated
    public AnvilGUI(Plugin plugin, Player holder, String insert, BiFunction<Player, String, String> biFunction) {
        this(plugin, holder, "Repair & Name", insert, new HashMap<>(), false, null, biFunction::apply);
    }

    /**
     * Create an AnvilGUI and open it for the player.
     *
     * @param plugin A {@link org.bukkit.plugin.java.JavaPlugin} instance
     * @param player The {@link Player} to open the inventory for
     * @param inventoryTitle What to have the text already set to
     * @param itemText The name of the item in the first slot of the anvilGui
     * @param items Items in gui
     * @param preventClose Whether to prevent the inventory from closing
     * @param closeListener A {@link Consumer} when the inventory closes
     * @param completeFunction A {@link BiFunction} that is called when the player clicks the {@link Slot#OUTPUT} slot
     */
    private AnvilGUI (
            Plugin plugin,
            Player player,
            String inventoryTitle,
            String itemText,
            Map<Integer, ItemStack> items,
            boolean preventClose,
            Consumer<InventoryCloseEvent> closeListener,
            BiFunction<Player, String, String> completeFunction
    ) {
        this.plugin = plugin;
        this.player = player;
        this.inventoryTitle = inventoryTitle;
        this.items = items;
        this.preventClose = preventClose;
        this.closeListener = closeListener;
        this.completeFunction = completeFunction;

        if(itemText != null) {
            if(!items.containsKey(0)) {
                this.items.put(0, new ItemStack(Material.PAPER));
            }

            ItemMeta paperMeta = this.items.get(0).getItemMeta();
            paperMeta.setDisplayName(itemText);
            this.items.get(0).setItemMeta(paperMeta);
        }

        openInventory();
    }

    /**
     * Opens the anvil GUI
     */
    private void openInventory() {
        WRAPPER.handleInventoryCloseEvent(player);
        WRAPPER.setActiveContainerDefault(player);

        Bukkit.getPluginManager().registerEvents(listener, plugin);

        final Object container = WRAPPER.newContainerAnvil(player, inventoryTitle);

        inventory = WRAPPER.toBukkitInventory(container);
        for (int i = 0; i < 2; i++) {
            if (!this.items.containsKey(i)) {
                continue;
            }
            inventory.setItem(i, this.items.get(i));

            containerId = WRAPPER.getNextContainerId(player, container);
            WRAPPER.sendPacketOpenWindow(player, containerId, inventoryTitle);
            WRAPPER.setActiveContainer(player, container);
            WRAPPER.setActiveContainerId(container, containerId);
            WRAPPER.addActiveContainerSlotListener(container, player);
            open = true;
        }
    }

    /**
     * Closes the inventory if it's open.
     */
    public void closeInventory () {
        if (!open) {
            return;
        }
        open = false;

        WRAPPER.handleInventoryCloseEvent(player);
        WRAPPER.setActiveContainerDefault(player);
        WRAPPER.sendPacketCloseWindow(player, containerId);

        HandlerList.unregisterAll(listener);
    }
    
    /**
     * * Returns the Bukkit inventory for this anvil gui
     * @return the {@link Inventory} for this anvil gui
     */
    public Inventory getInventory(){
        return inventory;
    }

    /**
     * Simply holds the listeners for the GUI
     */
    private class ListenUp implements Listener {

        @EventHandler
        public void onInventoryClick(InventoryClickEvent event) {
            if (((event.getInventory().equals(inventory)) && (event.getRawSlot() < 3)) ||
                    (event.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)) ||
                    ((event.getRawSlot() < 3) && ((event.getAction().equals(InventoryAction.PLACE_ALL)) || (event.getAction().equals(InventoryAction.PLACE_ONE)) || (event.getAction().equals(InventoryAction.PLACE_SOME)) || (event.getCursor() != null)))
            ) {
                event.setCancelled(true);
                final Player clicker = (Player) event.getWhoClicked();
                if (event.getRawSlot() == Slot.OUTPUT) {
                    final ItemStack clicked = inventory.getItem(Slot.OUTPUT);
                    if (clicked == null || clicked.getType() == Material.AIR) return;
                    
                    final String response = completeFunction.apply(clicker, clicked.hasItemMeta() ? clicked.getItemMeta().getDisplayName() : "");
                    if (response != null) {
                        final ItemMeta meta = clicked.getItemMeta();
                        meta.setDisplayName(response);
                        clicked.setItemMeta(meta);
                        inventory.setItem(Slot.INPUT_LEFT, clicked);
                    } else {
                        closeInventory();
                    }
                }
            }
        }

        @EventHandler
        public void onInventoryDrag(InventoryDragEvent event) {
            if (event.getInventory().equals(inventory)) {
                for (int slot : Slot.values()) {
                    if (event.getRawSlots().contains(slot)) {
                        event.setCancelled(true);
                        break;
                    }
                }
            }
        }

        @EventHandler
        public void onInventoryClose(InventoryCloseEvent event) {
            if (open && event.getInventory().equals(inventory)) {
                closeInventory();
                if (closeListener != null) {
                    closeListener.accept(event);
                }
                if (preventClose) {
                    Bukkit.getScheduler().runTask(plugin, AnvilGUI.this::openInventory);
                }
            }
        }

    }

    /**
     * A builder class for an {@link AnvilGUI} object
     */
    public static class Builder {
        
        private final Map<Integer, ItemStack> items = new HashMap<>();

        /**
         * An {@link Consumer} that is called when the anvil GUI is closed
         */
        private Consumer<InventoryCloseEvent> closeListener;
        /**
         * A state that decides where the anvil GUI is able to be closed by the user
         */
        private boolean preventClose = false;
        /**
         * An {@link BiFunction} that is called when the anvil output slot has been clicked
         */
        private BiFunction<Player, String, String> completeFunction;
        /**
         * The {@link Plugin} that this anvil GUI is associated with
         */
        private Plugin plugin;
        /**
         * The text that will be displayed to the user
         */
        private String title = "Repair & Name";
        /**
         * The starting text on the item
         */
        private String itemText = "";
        
        /**
         * Prevents the closing of the anvil GUI by the user
         *
         * @return The {@link Builder} instance
         */
        public Builder preventClose() {
            preventClose = true;
            return this;
        }

        /**
         * Listens for when the inventory is closed
         *
         * @param closeListener An {@link Consumer} that is called when the anvil GUI is closed
         * @return The {@link Builder} instance
         * @throws IllegalArgumentException when the closeListener is null
         */
        public Builder onClose(Consumer<InventoryCloseEvent> closeListener) {
            if (closeListener != null) {
                this.closeListener = closeListener;
            }
            return this;
        }

        /**
         * Handles the inventory output slot when it is clicked
         *
         * @param completeFunction An {@link BiFunction} that is called when the user clicks the output slot
         * @return The {@link Builder} instance
         * @throws IllegalArgumentException when the completeFunction is null
         */
        public Builder onComplete(BiFunction<Player, String, String> completeFunction) {
            Validate.notNull(completeFunction, "Complete function cannot be null");
            this.completeFunction = completeFunction;
            return this;
        }

        /**
         * Sets the plugin for the {@link AnvilGUI}
         *
         * @param plugin The {@link Plugin} this anvil GUI is associated with
         * @return The {@link Builder} instance
         * @throws IllegalArgumentException if the plugin is null
         */
        public Builder plugin(Plugin plugin) {
            Validate.notNull(plugin, "Plugin cannot be null");
            this.plugin = plugin;
            return this;
        }

        /**
         * Sets the inital item-text that is displayed to the user
         *
         * @param text The initial name of the item in the anvil
         * @return The {@link Builder} instance
         * @throws IllegalArgumentException if the text is null
         */
        public Builder text(String text) {
            if (text != null) {
                this.itemText = text;
            }
            return this;
        }
        
        /**
         * Sets the AnvilGUI title that is to be displayed to the user
         *
         * @param title The title that is to be displayed to the user
         * @return The {@link Builder} instance
         * @throws IllegalArgumentException if the title is null
         */
        public Builder title(String title) {
            if (title != null) {
                this.title = title;
            }
            return this;
        }


        public Builder setItem(final int slot, final ItemStack item) {
            Validate.notNull(slot, "Slot cannot be null");
            Validate.notNull(item, "Item cannot be null");
            this.items.put(slot, item);
            return this;
        }

        /**
         * Creates the anvil GUI and opens it for the player
         *
         * @param player The {@link Player} the anvil GUI should open for
         * @return The {@link AnvilGUI} instance from this builder
         * @throws IllegalArgumentException when the onComplete function, plugin, or player is null
         */
        public AnvilGUI open(Player player) {
            Validate.notNull(plugin, "Plugin cannot be null");
            Validate.notNull(completeFunction, "Complete function cannot be null");
            Validate.notNull(player, "Player cannot be null");
            return new AnvilGUI(plugin, player, title, itemText, items, preventClose, closeListener, completeFunction);
        }

    }

    /**
     * Class wrapping the magic constants of slot numbers in an anvil GUI
     */
    public static class Slot {
        
        private static final int[] values = new int[]{Slot.INPUT_LEFT, Slot.INPUT_RIGHT, Slot.OUTPUT};

        /**
         * The slot on the far left, where the first input is inserted. An {@link ItemStack} is always inserted
         * here to be renamed
         */
        public static final int INPUT_LEFT = 0;
        /**
         * Not used, but in a real anvil you are able to put the second item you want to combine here
         */
        public static final int INPUT_RIGHT = 1;
        /**
         * The output slot, where an item is put when two items are combined from {@link #INPUT_LEFT} and
         * {@link #INPUT_RIGHT} or {@link #INPUT_LEFT} is renamed
         */
        public static final int OUTPUT = 2;

        /**
         * Get all anvil slot values
         *
         * @return The array containing all possible anvil slots
         */
        public static int[] values() {
            return values;
        }
    }

}

