package thaumicenergistics.common.container;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotFurnace;
import net.minecraft.item.ItemStack;
import org.apache.commons.lang3.tuple.ImmutablePair;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.api.grid.ICraftingIssuerHost;
import thaumicenergistics.api.grid.IMEEssentiaMonitor;
import thaumicenergistics.api.grid.IMEEssentiaMonitorReceiver;
import thaumicenergistics.api.gui.IAspectSelectorContainer;
import thaumicenergistics.api.gui.ICraftingIssuerContainer;
import thaumicenergistics.api.storage.IAspectStack;
import thaumicenergistics.api.storage.IEssentiaRepo;
import thaumicenergistics.common.container.slot.SlotRestrictive;
import thaumicenergistics.common.network.packet.client.Packet_C_EssentiaCellTerminal;
import thaumicenergistics.common.network.packet.client.Packet_C_Sync;
import thaumicenergistics.common.network.packet.server.Packet_S_EssentiaCellTerminal;
import thaumicenergistics.common.storage.AspectStack;
import thaumicenergistics.common.storage.EssentiaRepo;
import thaumicenergistics.common.utils.EffectiveSide;
import thaumicenergistics.common.utils.ThEUtils;
import thaumicenergistics.integration.tc.EssentiaItemContainerHelper;
import thaumicenergistics.integration.tc.EssentiaItemContainerHelper.AspectItemType;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.security.PlayerSource;

/**
 * Base class for cell and terminal inventory containers
 * 
 * @author Nividica
 * 
 */
public abstract class ContainerEssentiaCellTerminalBase
	extends ContainerWithPlayerInventory
	implements IMEEssentiaMonitorReceiver, IAspectSelectorContainer, ICraftingIssuerContainer
{
	/**
	 * X position for the output slot
	 */
	private static final int OUTPUT_POSITION_X = 26;

	/**
	 * Y position for the output slot
	 */
	private static final int OUTPUT_POSITION_Y = 74;

	/**
	 * X position for the input slot
	 */
	private static final int INPUT_POSITION_X = 8;

	/**
	 * Y position for the input slot
	 */
	private static final int INPUT_POSITION_Y = 74;

	/**
	 * Y position for the player inventory
	 */
	private static final int PLAYER_INV_POSITION_Y = 104;

	/**
	 * Y position for the hotbar inventory
	 */
	private static final int HOTBAR_INV_POSITION_Y = 162;

	/**
	 * The minimum amount of time to wait before playing
	 * sounds again. In ms.
	 */
	private static final int MINIMUM_SOUND_WAIT = 900;

	/**
	 * The number of ticks required to pass before doWork is called.
	 */
	private static final int WORK_TICK_RATE = 3;

	/**
	 * The maximum amount of essentia to try and transfer each time
	 * the transfer method is called.
	 * This is a soft-cap.
	 */
	private static final int ESSENTIA_TRANSFER_PER_WORK_CYCLE = 64;

	/**
	 * Inventory indices for the input and output
	 */
	private static int OUTPUT_INV_INDEX = 1, INPUT_INV_INDEX = 0;

	/**
	 * Essentia network monitor
	 */
	protected IMEEssentiaMonitor monitor;

	/**
	 * List of aspects on the network
	 */
	protected final IEssentiaRepo repo;

	/**
	 * The aspect the user has selected.
	 */
	protected Aspect selectedAspect;

	/**
	 * The player that owns this container.
	 */
	protected EntityPlayer player;

	/**
	 * Import and export inventory
	 */
	protected IInventory inventory;

	/**
	 * The last known stack size stored in the export slot
	 */
	private int audioStackSizeTracker = 0;

	/**
	 * The last time, in ms, the transfer sound played
	 */
	private long lastSoundPlaytime = 0;

	/**
	 * Holds a list of changes sent to the gui before the
	 * full list is sent.
	 */
	private List<IAspectStack> pendingChanges = new ArrayList<IAspectStack>();

	/**
	 * Tracks the number of ticks
	 */
	private int tickCounter = 0;

	/**
	 * Tracks the amount of work performed.
	 */
	private int workCounter = 0;

	/**
	 * Work slots
	 */
	private Slot inputSlot, outputSlot;

	/**
	 * Location of the splash sound
	 */
	private final String soundLocation_Splash = "game.neutral.swim";

	/**
	 * Location of the paper sound
	 */
	private final String soundLocation_Paper = "thaumcraft:page";

	/**
	 * Set to true once a full list request is sent to the server.
	 */
	protected boolean hasRequested = false;

	/**
	 * Create the container and register the owner
	 * 
	 * @param player
	 */
	public ContainerEssentiaCellTerminalBase( final EntityPlayer player )
	{
		this.player = player;

		if( EffectiveSide.isClientSide() )
		{
			// Set the sound time
			this.lastSoundPlaytime = System.currentTimeMillis();
		}
		else
		{
			this.hasRequested = true;
		}

		// Create the reop
		this.repo = new EssentiaRepo();
	}

	/**
	 * Returns true if the specified stack can be merged into the output slot.
	 * 
	 * @param stackToMerge
	 * @return True if the slot is empty,
	 * or if can be merged by increasing the slots stacksize by the specified stacks stacksize.
	 */
	private boolean canMergeWithOutputSlot( final ItemStack stackToMerge )
	{
		// Ensure the stack is not null.
		if( stackToMerge == null )
		{
			// Invalid itemstack
			return false;
		}

		// Is the slot empty?
		if( !this.outputSlot.getHasStack() )
		{
			return true;
		}

		// Get the slot stack
		ItemStack slotStack = this.outputSlot.getStack();

		// Get the stack size
		int slotStackSize = slotStack.stackSize;

		// Is the slot full?
		if( slotStack.getMaxStackSize() == slotStackSize )
		{
			return false;
		}

		// Will adding the stack cause the slot to be over full?
		if( ( slotStackSize + stackToMerge.stackSize ) > slotStack.getMaxStackSize() )
		{
			return false;
		}

		// Do the stacks match?
		// Compare ignoring stack size
		ItemStack o = slotStack.copy();
		ItemStack n = stackToMerge.copy();
		o.stackSize = 1;
		n.stackSize = 1;
		return ItemStack.areItemStacksEqual( o, n );
	}

	/**
	 * Returns if the player has the requested permission or not.
	 * 
	 * @param perm
	 * @param actionSource
	 * @return
	 */
	private boolean checkSecurityPermission( final SecurityPermissions perm, final BaseActionSource actionSource )
	{

		// Ensure there is an action source.
		if( actionSource == null )
		{
			return false;
		}

		// Get the source node
		IGridNode sourceNode = null;
		if( actionSource instanceof MachineSource )
		{
			sourceNode = ( (MachineSource)actionSource ).via.getActionableNode();
		}
		else if( actionSource instanceof PlayerSource )
		{
			sourceNode = ( (PlayerSource)actionSource ).via.getActionableNode();
		}

		// Ensure there is a node
		if( sourceNode == null )
		{
			return false;
		}

		// Get the security grid for the node.
		ISecurityGrid sGrid = sourceNode.getGrid().getCache( ISecurityGrid.class );

		// Return the permission.
		return sGrid.hasPermission( this.player, perm );
	}

	/**
	 * Drains an essentia container item.
	 * 
	 * @param container
	 * @param actionSource
	 * @param mode
	 * @return The result of the drain. <AmountDrained, NewContainer>
	 */
	@Nullable
	private ImmutablePair<Integer, ItemStack> drainContainer( final ItemStack container, final BaseActionSource actionSource,
																final Actionable mode )
	{
		// Ensure there is a container
		if( container == null )
		{
			return null;
		}

		// Get the fluid stack from the item
		IAspectStack containerEssentia = EssentiaItemContainerHelper.INSTANCE.getAspectStackFromContainer( container );

		// Ensure there is something to drain
		if( ( containerEssentia == null ) || containerEssentia.isEmpty() )
		{
			// Nothing to drain
			return null;
		}

		// Get the proposed drain amount.
		int proposedDrainAmount = (int)containerEssentia.getStackSize();

		// Do a network injection
		long rejectedAmount = this.monitor.injectEssentia( containerEssentia.getAspect(), proposedDrainAmount, mode, actionSource,
			true );

		// Was any rejected?
		if( rejectedAmount > 0 )
		{
			// Decrease the proposed amount
			proposedDrainAmount -= (int)rejectedAmount;

			// Can the network accept any?
			if( proposedDrainAmount <= 0 )
			{
				// Network is full
				return null;
			}
		}

		// Adjust work counter
		if( mode == Actionable.MODULATE )
		{
			this.workCounter += proposedDrainAmount;
		}

		// Attempt to drain the container
		return EssentiaItemContainerHelper.INSTANCE.extractFromContainer( container, proposedDrainAmount );
	}

	/**
	 * Fills an essentia container item.
	 * 
	 * @param withAspect
	 * @param container
	 * @param actionSource
	 * @param mode
	 * @return The result of the fill. <AmountFilled, NewContainer>
	 */
	@Nullable
	private ImmutablePair<Integer, ItemStack> fillContainer( final Aspect withAspect, final ItemStack container,
																final BaseActionSource actionSource, final Actionable mode )
	{
		// Ensure there is an aspect
		if( withAspect == null )
		{
			return null;
		}

		// Get the capacity of the container
		int containerCapacity = EssentiaItemContainerHelper.INSTANCE.getContainerCapacity( container );

		// Can the container hold essentia?
		if( containerCapacity == 0 )
		{
			// Invalid container
			return null;
		}

		// Do an extraction from the network
		long extractedAmount = this.monitor.extractEssentia( withAspect, containerCapacity, mode, actionSource, true );

		// Was anything extracted?
		if( extractedAmount <= 0 )
		{
			// Gas is not present on network.
			return null;
		}

		// Calculate the proposed amount, based on how much we need and how much
		// is available
		int proposedFillAmount = (int)Math.min( containerCapacity, extractedAmount );

		// Adjust work counter
		if( mode == Actionable.MODULATE )
		{
			this.workCounter += proposedFillAmount;
		}

		// Create a new container filled to the proposed amount
		return EssentiaItemContainerHelper.INSTANCE.injectIntoContainer( container,
			new AspectStack( withAspect, proposedFillAmount ) );
	}

	/**
	 * Attach this container to the Essentia monitor
	 */
	protected void attachToMonitor()
	{
		if( ( EffectiveSide.isServerSide() ) && ( this.monitor != null ) )
		{
			this.monitor.addListener( this, this.monitor.hashCode() );

			// Update our cached list of aspects
			this.repo.copyFrom( this.monitor.getEssentiaList() );

		}
	}

	/**
	 * Binds the container to the specified inventory and the players inventory.
	 * 
	 * @param inventory
	 */
	protected void bindToInventory( final IInventory inventory )
	{
		// Set the inventory
		this.inventory = inventory;

		// Create the input slot
		this.inputSlot = new SlotRestrictive( inventory, ContainerEssentiaCellTerminalBase.INPUT_INV_INDEX,
						ContainerEssentiaCellTerminalBase.INPUT_POSITION_X, ContainerEssentiaCellTerminalBase.INPUT_POSITION_Y );
		this.addSlotToContainer( this.inputSlot );

		// Create the output slot
		this.outputSlot = new SlotFurnace( this.player, inventory, ContainerEssentiaCellTerminalBase.OUTPUT_INV_INDEX,
						ContainerEssentiaCellTerminalBase.OUTPUT_POSITION_X, ContainerEssentiaCellTerminalBase.OUTPUT_POSITION_Y );
		this.addSlotToContainer( this.outputSlot );

		// Bind to the player's inventory
		this.bindPlayerInventory( this.player.inventory, ContainerEssentiaCellTerminalBase.PLAYER_INV_POSITION_Y,
			ContainerEssentiaCellTerminalBase.HOTBAR_INV_POSITION_Y );

	}

	/**
	 * Detaches from the monitor if attached.
	 */
	protected void detachFromMonitor()
	{
		if( EffectiveSide.isServerSide() )
		{
			if( this.monitor != null )
			{
				// Stop listening
				this.monitor.removeListener( this );

				// Null the monitor
				this.monitor = null;

				// Clear the repo
				this.repo.clear();
			}
		}
	}

	/**
	 * Called periodically so that the container can perform work.
	 */
	protected abstract void doWork( int elapsedTicks );

	/**
	 * Gets the action source.
	 * 
	 * @return
	 */
	protected abstract BaseActionSource getActionSource();

	/**
	 * Fills, drains, or sets label aspect.
	 * 
	 * @param stack
	 * This is not modified during the course of this function.
	 * @param aspect
	 * Ignored when draining
	 * @param actionSource
	 * @param mode
	 * @return The new stack if changes made, the original stack otherwise.
	 */
	protected ItemStack transferEssentia( final ItemStack stack, final Aspect aspect, final BaseActionSource actionSource, final Actionable mode )
	{
		// Ensure the stack & monitor are not null
		if( ( stack == null ) || ( this.monitor == null ) )
		{
			return stack;
		}

		// Get the item type
		AspectItemType iType = EssentiaItemContainerHelper.INSTANCE.getItemType( stack );

		// Label?
		if( iType == AspectItemType.JarLabel )
		{
			// Copy the stack
			ItemStack label = stack.copy();

			// Set the label
			EssentiaItemContainerHelper.INSTANCE.setLabelAspect( label, aspect );

			// Update work performed
			++this.workCounter;

			// Return the label
			return label;
		}

		// Valid container?
		if( iType != AspectItemType.EssentiaContainer )
		{
			// Invalid container
			return stack;
		}

		// Result of the operation
		ImmutablePair<Integer, ItemStack> result = null;

		// Filling?
		if( EssentiaItemContainerHelper.INSTANCE.isContainerEmpty( stack ) )
		{
			// Check extract permission
			if( this.checkSecurityPermission( SecurityPermissions.EXTRACT, actionSource ) )
			{
				// Attempt to fill the container
				result = this.fillContainer( aspect, stack, actionSource, mode );
			}
		}
		// Draining
		else
		{
			// Check inject permission
			if( this.checkSecurityPermission( SecurityPermissions.INJECT, actionSource ) )
			{
				// Attempt to drain the container
				result = this.drainContainer( stack, actionSource, mode );
			}
		}

		// Is there any result?
		if( result != null )
		{
			// Return the new stack.
			return result.right;
		}

		// No result
		return stack;
	}

	/**
	 * Transfers essentia in or out of the system using the input and output slots.
	 */
	protected void transferEssentiaFromWorkSlots()
	{
		// Ensure the inventory is valid
		if( this.inventory == null )
		{
			return;
		}

		// Get the input stack
		final ItemStack inputStack = this.inventory.getStackInSlot( ContainerEssentiaCellTerminalBase.INPUT_INV_INDEX );

		// Ensure the input stack is not empty
		if( ( inputStack == null ) )
		{
			// Nothing in input slot.
			return;
		}

		// Is the output slot full?
		if( this.outputSlot.getHasStack() )
		{
			ItemStack outputStack = this.outputSlot.getStack();
			if( outputStack.stackSize >= outputStack.getMaxStackSize() )
			{
				// Output slot is full.
				return;
			}
		}

		// Reset the work counter
		this.workCounter = 0;

		// Get the action source
		final BaseActionSource actionSource = this.getActionSource();

		// Copy the input
		final ItemStack container = inputStack.copy();
		container.stackSize = 1;

		// Loop while maximum work has not been performed, there is input, and the operation did not fail.
		ItemStack result;
		do
		{
			// Simulate the work
			result = this.transferEssentia( container, this.selectedAspect, actionSource, Actionable.SIMULATE );

			// Did anything change?
			if( ( result == null ) || ( result == container ) )
			{
				// No work to perform
				break;
			}

			// Can the result not be merged with the output stack?
			if( !this.canMergeWithOutputSlot( result ) )
			{
				// Unable to merge
				break;
			}

			// Perform the work
			result = this.transferEssentia( container, this.selectedAspect, actionSource, Actionable.MODULATE );

			// Merge the result
			if( this.outputSlot.getHasStack() )
			{
				// Can just increment here because canMergeWithOutputSlot explicitly checks that
				++this.outputSlot.getStack().stackSize;
			}
			else
			{
				this.outputSlot.putStack( result );
			}

			// Is the input drained?
			if( ( --inputStack.stackSize ) == 0 )
			{
				this.inputSlot.putStack( null );
				break;
			}
		}
		while( this.workCounter < ESSENTIA_TRANSFER_PER_WORK_CYCLE );
	}

	/**
	 * Who can interact with the container?
	 */
	@Override
	public boolean canInteractWith( final EntityPlayer player )
	{
		return true;
	}

	/**
	 * Checks if there is any work to perform.
	 * If there is it does so.
	 */
	@Override
	public final void detectAndSendChanges()
	{
		// Call super
		super.detectAndSendChanges();

		// Inc tick tracker
		this.tickCounter += 1;

		if( this.tickCounter > ContainerEssentiaCellTerminalBase.WORK_TICK_RATE )
		{
			// Do work
			this.doWork( this.tickCounter );

			// Reset the tick counter
			this.tickCounter = 0;
		}
	}

	/**
	 * Gets the list of aspect stacks in the container.
	 * 
	 * @return
	 */
	public Collection<IAspectStack> getAspectStackList()
	{
		return this.repo.getAll();
	}

	@Override
	public abstract ICraftingIssuerHost getCraftingHost();

	/**
	 * Get the player that owns this container
	 * 
	 * @return
	 */
	public EntityPlayer getPlayer()
	{
		return this.player;
	}

	/**
	 * Gets the aspect that the player has selected.
	 * 
	 * @return
	 */
	public Aspect getSelectedAspect()
	{
		return this.selectedAspect;
	}

	/**
	 * Is this container still valid for receiving updates
	 * from the AE monitor?
	 */
	@Override
	public boolean isValid( final Object verificationToken )
	{
		if( this.monitor == null )
		{
			return false;
		}

		// Do the hash codes match?
		if( this.monitor.hashCode() == (Integer)verificationToken )
		{
			return true;
		}

		// No longer valid
		this.monitor = null;
		this.repo.clear();

		return false;
	}

	/**
	 * Called when a client has clicked on a craftable aspect.
	 * 
	 * @param player
	 * @param result
	 */
	public abstract void onClientRequestAutoCraft( final EntityPlayer player, final Aspect aspect );

	/**
	 * Called when a client requests the state of the container.
	 * Updates our cached list of aspects
	 */
	public abstract void onClientRequestFullUpdate();

	/**
	 * Called when a client sends a sorting mode change request.
	 * 
	 * @param sortingMode
	 */
	public abstract void onClientRequestSortModeChange( final EntityPlayer player, boolean backwards );

	/**
	 * Called when a client sends a view mode change request.
	 * 
	 * @param player
	 */
	public abstract void onClientRequestViewModeChange( final EntityPlayer player, boolean backwards );

	/**
	 * Unregister this container from the monitor.
	 */
	@Override
	public void onContainerClosed( final EntityPlayer player )
	{
		super.onContainerClosed( player );

		this.detachFromMonitor();
	}

	/**
	 * Called when a player clicked on an aspect while holding an item.
	 * 
	 * @param player
	 * @param aspect
	 */
	public void onInteractWithHeldItem( final EntityPlayer player, final Aspect aspect )
	{
		// Sanity check
		if( ( player == null || ( player.inventory.getItemStack() == null ) ) )
		{
			return;
		}

		// Get the item
		ItemStack sourceStack = player.inventory.getItemStack();

		// Create a new stack
		final ItemStack takeFrom = sourceStack.copy();
		takeFrom.stackSize = 1;

		// Get the action source
		final BaseActionSource actionSource = this.getActionSource();

		// Simulate the transfer
		ItemStack resultStack = this.transferEssentia( takeFrom, aspect, actionSource, Actionable.SIMULATE );

		// Was any work performed?
		if( ( resultStack == null ) || ( resultStack == takeFrom ) )
		{
			// Nothing to do.
			return;
		}

		// If the source stack size is > 1, the result will need to be put into the player inventory
		if( sourceStack.stackSize > 1 )
		{
			// Attempt to merge
			if( !this.mergeSlotWithHotbarInventory( resultStack ) )
			{
				if( !this.mergeSlotWithPlayerInventory( resultStack ) )
				{
					// Could not merge
					return;
				}
			}

			// Decrement the source stack
			--sourceStack.stackSize;
		}
		else
		{
			// Set the source stack to the result
			sourceStack = resultStack;
		}

		// Perform the work
		this.transferEssentia( takeFrom, aspect, actionSource, Actionable.MODULATE );

		// Set what the player is holding
		player.inventory.setItemStack( sourceStack );

		// Update
		Packet_C_Sync.sendPlayerHeldItem( player, sourceStack );
		this.detectAndSendChanges();
		this.playTransferSound( player, false,
			( EssentiaItemContainerHelper.INSTANCE.getItemType( sourceStack ) == AspectItemType.JarLabel ? 1 : 0 ) );

	}

	/**
	 * Called by the gui when the aspect list arrives.
	 * 
	 * @param aspectStackList
	 */
	public void onReceivedAspectList( final Collection<IAspectStack> aspectStackList )
	{
		// Set the aspect list
		this.repo.copyFrom( aspectStackList );

		// Check pending changes
		if( ( aspectStackList != null ) && ( !this.pendingChanges.isEmpty() ) )
		{
			// Update list with pending changes
			for( int index = 0; index < this.pendingChanges.size(); index++ )
			{
				this.onReceivedAspectListChange( this.pendingChanges.get( index ) );
			}

			// Clear pending
			this.pendingChanges.clear();
		}
	}

	/**
	 * Called by the gui when a change arrives.
	 * 
	 * @param change
	 * @return True if the repo is prepared to be displayed.
	 */
	public boolean onReceivedAspectListChange( final IAspectStack change )
	{
		// Ignored server side
		if( EffectiveSide.isServerSide() )
		{
			return false;
		}

		// Ensure the change is not null
		if( change == null )
		{
			return false;
		}

		// Have we requested the full list yet?
		if( !this.hasRequested )
		{
			return false;
		}

		// Has the full list been received?
		if( this.repo.isEmpty() )
		{
			// Not yet received full list, add to pending
			this.pendingChanges.add( change );
			return false;
		}

		// Post the change
		this.repo.postChange( change );

		// The GUI can update if there are no pending changes
		return this.pendingChanges.isEmpty();
	}

	/**
	 * Called when the the selected aspect has changed.
	 * 
	 * @param selectedAspect
	 */
	public void onReceivedSelectedAspect( final Aspect selectedAspect )
	{
		// Set the selected aspect
		this.selectedAspect = selectedAspect;

		// Is this server side?
		if( EffectiveSide.isServerSide() )
		{
			// Send the change back to the client
			Packet_C_EssentiaCellTerminal.setSelectedAspect( this.player, this.selectedAspect );
		}
	}

	/**
	 * Checks if the transfer sound should play.
	 * if checkWorkSlots is true the type will be automatically determined.
	 * 
	 * @param player
	 * @param checkWorkSlots
	 * @param type
	 * 0 = splash, 1 = paper
	 */

	public void playTransferSound( final EntityPlayer player, final boolean checkWorkSlots, int type )
	{
		if( checkWorkSlots )
		{
			// Get the itemstack in the output slot
			ItemStack itemStack = this.outputSlot.getStack();

			// Is there anything in the second slot?
			if( itemStack != null )
			{

				// Is the item a label?
				if( EssentiaItemContainerHelper.INSTANCE.getItemType( itemStack ) == AspectItemType.JarLabel )
				{
					type = 1;
				}
				else
				{
					type = 0;
				}

				// Has the count changed?
				if( this.audioStackSizeTracker == itemStack.stackSize )
				{
					// Nothing changed
					return;
				}

				// Set the count
				this.audioStackSizeTracker = itemStack.stackSize;

			}
			else
			{
				// Reset the count
				this.audioStackSizeTracker = 0;
				return;
			}

		}

		// Has enough time passed to play the sound again?
		if( ( System.currentTimeMillis() - this.lastSoundPlaytime ) > MINIMUM_SOUND_WAIT )
		{
			if( type == 0 )
			{
				ThEUtils.playClientSound( this.player, this.soundLocation_Splash );
			}
			else if( type == 1 )
			{
				ThEUtils.playClientSound( this.player, this.soundLocation_Paper );
			}

			// Set the playtime
			this.lastSoundPlaytime = System.currentTimeMillis();
		}
	}

	/**
	 * Called by the Essentia monitor when the network changes.
	 */

	@Override
	public final void postChange( final IMEEssentiaMonitor fromMonitor, final Iterable<IAspectStack> changes )
	{
		// Ensure there was a change
		if( changes == null )
		{
			return;
		}

		// Loop over the changes
		for( IAspectStack change : changes )
		{
			// Update the client
			Packet_C_EssentiaCellTerminal.setAspectAmount( this.player, change );
		}
	}

	@Override
	public void putStackInSlot( final int slotNumber, final ItemStack stack )
	{
		// Call super
		super.putStackInSlot( slotNumber, stack );

		// Is this client side?
		if( ( this.outputSlot.slotNumber == slotNumber ) && EffectiveSide.isClientSide() )
		{
			this.playTransferSound( null, true, 0 );
		}
	}

	/**
	 * Called when the user has clicked on an aspect.
	 * Sends that change to the server for validation.
	 */
	@Override
	public void setSelectedAspect( final Aspect selectedAspect )
	{
		Packet_S_EssentiaCellTerminal.sendSelectedAspect( this.player, selectedAspect );
	}

	@Override
	public ItemStack transferStackInSlot( final EntityPlayer player, final int slotNumber )
	{
		// Get the slot that was shift-clicked
		Slot slot = (Slot)this.inventorySlots.get( slotNumber );

		// Is there a valid slot with and item?
		if( ( slot != null ) && ( slot.getHasStack() ) )
		{
			boolean didMerge = false;

			// Get the itemstack in the slot
			ItemStack slotStack = slot.getStack();

			// Was the slot clicked the input slot or output slot?
			if( ( slot == this.inputSlot ) || ( slot == this.outputSlot ) )
			{
				// Attempt to merge with the player inventory
				didMerge = this.mergeSlotWithPlayerInventory( slotStack );
			}
			// Was the slot clicked in the player or hotbar inventory?
			else if( this.slotClickedWasInPlayerInventory( slotNumber ) || this.slotClickedWasInHotbarInventory( slotNumber ) )
			{
				// Is the item valid for the input slot?
				if( this.inputSlot.isItemValid( slotStack ) )
				{
					// Attempt to merge with the input slot
					didMerge = this.mergeItemStack( slotStack, this.inputSlot.slotNumber, this.inputSlot.slotNumber + 1, false );
				}

				// Did we merge?
				if( !didMerge )
				{
					didMerge = this.swapSlotInventoryHotbar( slotNumber, slotStack );
				}

			}

			if( didMerge )
			{
				// Did the merger drain the stack?
				if( slotStack.stackSize == 0 )
				{
					// Set the slot to have no item
					slot.putStack( null );
				}
				else
				{
					// Inform the slot its stack changed;
					slot.onSlotChanged();
				}
			}

		}

		return null;
	}
}