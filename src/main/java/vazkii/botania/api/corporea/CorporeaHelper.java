/**
 * This class was created by <Vazkii>. It's distributed as
 * part of the Botania Mod. Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 *
 * File Created @ [Feb 14, 2015, 3:28:54 PM (GMT)]
 */
package vazkii.botania.api.corporea;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import vazkii.botania.api.BotaniaAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import static vazkii.botania.api.corporea.CorporeaRequestDefaultMatchers.*;

public final class CorporeaHelper {

	private static final List<InvWithLocation> empty = ImmutableList.of();
	private static final WeakHashMap<List<ICorporeaSpark>, List<InvWithLocation>> cachedNetworks = new WeakHashMap<>();

	/**
	 * How many items were matched in the last request. If java had "out" params like C# this wouldn't be needed :V
	 */
	public static int lastRequestMatches = 0;
	/**
	 * How many items were extracted in the last request.
	 */
	public static int lastRequestExtractions = 0;

	/**
	 * Gets a list of all the inventories on this spark network. This list is cached for use once every tick,
	 * and if something changes during that tick it'll still have the first result.
	 */
	public static List<InvWithLocation> getInventoriesOnNetwork(ICorporeaSpark spark) {
		ICorporeaSpark master = spark.getMaster();
		if(master == null)
			return empty;
		List<ICorporeaSpark> network = master.getConnections();

		if(cachedNetworks.containsKey(network)) {
			List<InvWithLocation> cache = cachedNetworks.get(network);
			if(cache != null)
				return cache;
		}

		List<InvWithLocation> inventories = new ArrayList<>();
		if(network != null)
			for(ICorporeaSpark otherSpark : network)
				if(otherSpark != null) {
					InvWithLocation inv = otherSpark.getSparkInventory();
					if(inv != null)
						inventories.add(inv);
				}

		cachedNetworks.put(network, inventories);
		return inventories;
	}

	/**
	 * Gets the number of available items in the network satisfying the given matcher.
	 * The higher level functions that use a List< IInventory > or a Map< IInventory, Integer > should be
	 * called instead if the context for those exists to avoid having to get the values again.
	 */
	public static int getCountInNetwork(CorporeaRequestMatcher matcher, ICorporeaSpark spark) {
		List<InvWithLocation> inventories = getInventoriesOnNetwork(spark);
		return getCountInNetwork(matcher, inventories);
	}

	/**
	 * Gets the number of available items in the network satisfying the given matcher.
	 * The higher level function that use a Map< IInventory, Integer > should be
	 * called instead if the context for this exists to avoid having to get the value again.
	 */
	public static int getCountInNetwork(CorporeaRequestMatcher matcher, List<InvWithLocation> inventories) {
		Map<InvWithLocation, Integer> map = getInventoriesWithMatchInNetwork(matcher, inventories);
		return getCountInNetwork(matcher, map);
	}

	/**
	 * Gets the amount of available items in the network of the type passed in, checking NBT or not.
	 */
	public static int getCountInNetwork(CorporeaRequestMatcher matcher, Map<InvWithLocation, Integer> inventories) {
		int count = 0;

		for(int value : inventories.values())
			count += value;

		return count;
	}

	/**
	 * Gets a Map mapping IInventories to the number of matching items.
	 * The higher level function that use a List< IInventory > should be
	 * called instead if the context for this exists to avoid having to get the value again.
	 */
	public static Map<InvWithLocation, Integer> getInventoriesWithMatchInNetwork(CorporeaRequestMatcher matcher, ICorporeaSpark spark) {
		List<InvWithLocation> inventories = getInventoriesOnNetwork(spark);
		return getInventoriesWithMatchInNetwork(matcher, inventories);
	}

	/**
	 * Gets a Map mapping IInventories to the number of matching items.
	 * The deeper level function that use a List< IInventory > should be
	 * called instead if the context for this exists to avoid having to get the value again.
	 */
	public static Map<InvWithLocation, Integer> getInventoriesWithMatchInNetwork(CorporeaRequestMatcher matcher, List<InvWithLocation> inventories) {
		Map<InvWithLocation, Integer> countMap = new HashMap<>();
		List<IWrappedInventory> wrappedInventories = BotaniaAPI.internalHandler.wrapInventory(inventories);
		for (IWrappedInventory inv : wrappedInventories) {
			CorporeaRequest request = new CorporeaRequest(matcher, -1);
			inv.countItems(request);
			if (request.foundItems > 0) {
				countMap.put(inv.getWrappedObject(), request.foundItems);
			}
		}

		return countMap;
	}

	/**
	 * Create a CorporeaRequestMatcher from an ItemStack and NBT-checkness.
	 */
	public static CorporeaRequestMatcher createMatcher(ItemStack stack, boolean checkNBT) {
		return new CorporeaItemStackMatcher(stack, checkNBT);
	}

	/**
	 * Create a CorporeaRequestMatcher from a String.
	 */
	public static CorporeaRequestMatcher createMatcher(String name) {
		return new CorporeaStringMatcher(name);
	}

	/**
	 * Bridge for requestItem() using an ItemStack.
	 */
	public static List<ItemStack> requestItem(ItemStack stack, ICorporeaSpark spark, boolean checkNBT, boolean doit) {
		return requestItem(createMatcher(stack, checkNBT), stack.getCount(), spark, doit);
	}

	/**
	 * Bridge for requestItem() using a String and an item count.
	 */
	public static List<ItemStack> requestItem(String name, int count, ICorporeaSpark spark, boolean doit) {
		return requestItem(createMatcher(name), count, spark, doit);
	}

	/**
	 * Requests list of ItemStacks of the type passed in from the network, or tries to, checking NBT or not.
	 * This will remove the items from the adequate inventories unless the "doit" parameter is false.
	 * Returns a new list of ItemStacks of the items acquired or an empty list if none was found.
	 * Case itemCount is -1 it'll find EVERY item it can.
	 * <br><br>
	 * The "matcher" parameter has to be an ItemStack or a String, if the first it'll check if the
	 * two stacks are similar using the "checkNBT" parameter, else it'll check if the name of the item
	 * equals or matches (case a regex is passed in) the matcher string.
	 * <br><br>
	 * When requesting counting of items, individual stacks may exceed maxStackSize for
	 * purposes of counting huge amounts.
	 */
	public static List<ItemStack> requestItem(CorporeaRequestMatcher matcher, int itemCount, ICorporeaSpark spark, boolean doit) {
		List<ItemStack> stacks = new ArrayList<>();
		CorporeaRequestEvent event = new CorporeaRequestEvent(matcher, itemCount, spark, doit);
		if(MinecraftForge.EVENT_BUS.post(event))
			return stacks;

		List<InvWithLocation> inventories = getInventoriesOnNetwork(spark);

		List<IWrappedInventory> inventoriesW = BotaniaAPI.internalHandler.wrapInventory(inventories);
		Map<ICorporeaInterceptor, ICorporeaSpark> interceptors = new HashMap<ICorporeaInterceptor, ICorporeaSpark>();

		CorporeaRequest request = new CorporeaRequest(matcher, itemCount);
		for(IWrappedInventory inv : inventoriesW) {
			ICorporeaSpark invSpark = inv.getSpark();

			InvWithLocation originalInventory = inv.getWrappedObject();
			if(originalInventory.world.getTileEntity(originalInventory.pos) instanceof ICorporeaInterceptor) {
				ICorporeaInterceptor interceptor = (ICorporeaInterceptor) originalInventory.world.getTileEntity(originalInventory.pos);
				interceptor.interceptRequest(matcher, itemCount, invSpark, spark, stacks, inventories, doit);
				interceptors.put(interceptor, invSpark);
			}

			if(doit) {
				stacks.addAll(inv.extractItems(request));
			} else {
				stacks.addAll(inv.countItems(request));
			}
		}

		for(ICorporeaInterceptor interceptor : interceptors.keySet())
			interceptor.interceptRequestLast(matcher, itemCount, interceptors.get(interceptor), spark, stacks, inventories, doit);

		lastRequestMatches = request.foundItems;
		lastRequestExtractions = request.extractedItems;

		return stacks;
	}

	/**
	 * Gets the spark attached to the inventory passed case it's a TileEntity.
	 */
	public static ICorporeaSpark getSparkForInventory(InvWithLocation inv) {
		TileEntity tile = inv.world.getTileEntity(inv.pos);
		return getSparkForBlock(tile.getWorld(), tile.getPos());
	}

	/**
	 * Gets the spark attached to the block in the coords passed in. Note that the coords passed
	 * in are for the block that the spark will be on, not the coords of the spark itself.
	 */
	public static ICorporeaSpark getSparkForBlock(World world, BlockPos pos) {
		List<Entity> sparks = world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(pos.up(), pos.add(1, 2, 1)), Predicates.instanceOf(ICorporeaSpark.class));
		return sparks.isEmpty() ? null : (ICorporeaSpark) sparks.get(0);
	}

	/**
	 * Gets if the block in the coords passed in has a spark attached. Note that the coords passed
	 * in are for the block that the spark will be on, not the coords of the spark itself.
	 */
	public static boolean doesBlockHaveSpark(World world, BlockPos pos) {
		return getSparkForBlock(world, pos) != null;
	}

	/**
	 * Clears the cached networks, called once per tick, should not be called outside
	 * of the botania code.
	 */
	public static void clearCache() {
		cachedNetworks.clear();
	}
	
	/** 
	 * Returns the comparator strength for a corporea request that corporea crystal cubes and retainers use, following the usual "each step up requires double the items" formula.
	 */
	public static int signalStrengthForRequestSize(int requestSize) {
		if(requestSize <= 0) return 0;
		else if (requestSize >= 16384) return 15;
		else return Math.min(15, MathHelper.log2(requestSize) + 1);
	}
}
