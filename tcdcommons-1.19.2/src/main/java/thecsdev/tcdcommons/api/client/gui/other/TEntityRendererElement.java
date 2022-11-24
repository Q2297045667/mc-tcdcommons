package thecsdev.tcdcommons.api.client.gui.other;

import static thecsdev.tcdcommons.api.client.registry.TCDCommonsClientRegistry.getEntityRendererSizeOffset;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.MapMaker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.MultilineText;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import thecsdev.tcdcommons.api.client.gui.TElement;
import thecsdev.tcdcommons.api.client.gui.util.FocusOrigin;
import thecsdev.tcdcommons.api.util.TextUtils;

public class TEntityRendererElement extends TElement
{
	// ==================================================
	// public static Text TEXT_AIR = TextUtils.fTranslatable("block.minecraft.air");
	/**
	 * Holds a map of "summoned" aka "cached" local entities for
	 * rendering based on various {@link EntityType}s.
	 */
	private static final ConcurrentMap<EntityType<?>, Entity> ENTITY_CACHE = new MapMaker().weakKeys().weakValues().makeMap();
	private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
	// --------------------------------------------------
	public static @Nullable Entity getCachedEntityFromType(EntityType<?> entityType)
	{
		//check arguments
		if(entityType == null || !entityType.isSummonable() || CLIENT.world == null)
			return null;
		
		//check if an entry already exists
		if(ENTITY_CACHE.containsKey(entityType))
			return ENTITY_CACHE.get(entityType);
		
		//create a new entity and put it in the cache
		Entity newEntity = null;
		try { newEntity = entityType.create(CLIENT.world); }
		catch(Exception e)
		{
			//some entities might not behave as expected, and
			//may throw exceptions upon their creation. deal with this
			newEntity = EntityType.ARMOR_STAND.create(CLIENT.world);
		}
		try { newEntity.discard(); }
		catch(Exception e) { /*again, deal with unexpected behavior from modded entities*/ }
		
		//assign cache and return the entity
		if(newEntity != null) ENTITY_CACHE.put(entityType, newEntity);
		return newEntity;
	}
	// ==================================================
	protected double scale;
	protected LivingEntity livingEntity;
	protected MultilineText entityTypeName;
	// --------------------------------------------------
	/**
	 * The center XY coordinates for rendering the
	 * {@link #livingEntity}. tY is for text.
	 */
	protected int cX, cY, tY;
	// --------------------------------------------------
	protected int cache_mobSize;
	//protected final Point cache_mobOffset; -- not yet...
	// --------------------------------------------------
	protected final BiConsumer<Integer, Integer> MOVED_HANDLER;
	// ==================================================
	public TEntityRendererElement(int x, int y, int width, int height) { this(x, y, width, height, null); }
	public TEntityRendererElement(int x, int y, int width, int height, EntityType<?> entityType)
	{
		super(x, y, width, height);
		setScale(0.9);
		setEntityType(entityType);
		recalcCache_cXY();
		
		MOVED_HANDLER = getEvents().MOVED.addWeakEventHandler((dX, dY) -> recalcCache_cXY());
	}
	// --------------------------------------------------
	public @Override boolean isClickThrough() { return true; }
	public @Override boolean canChangeFocus(FocusOrigin focusOrigin, boolean gainingFocus) { return !gainingFocus; }
	// ==================================================
	/**
	 * Returns the scale at which the {@link #getEntityType()}
	 * will be rendered on the screen.
	 */
	public double getScale() { return this.scale; }
	
	/**
	 * Sets {@link #getScale()}.
	 * @param scale The new scale.
	 */
	public void setScale(double scale) { this.scale = MathHelper.clamp(scale, 0.1, 5); recalcCache_mobSize(); }
	
	/**
	 * Recalculates the value of {@link #cache_mobSize}.
	 */
	protected void recalcCache_mobSize()
	{
		//calculate mob size
		if(this.livingEntity == null) { this.cache_mobSize = 30; return; }
		int w = getTpeWidth(), h = getTpeHeight();
		this.cache_mobSize = (int)(getLivingEntityGUISize(this.livingEntity, Math.min(w, h)) * getScale());
	}
	
	/**
	 * Recalculates the values of {@link #cX} and {@link #cY}.
	 */
	protected void recalcCache_cXY()
	{
		//calculate center XY
		this.cX = (getTpeX() + (getTpeWidth() / 2));
		this.cY = (getTpeEndY() - (getTpeHeight() / 4));
		//calculate text Y for the entity name text
		if(this.entityTypeName != null)
			tY = cY - (this.entityTypeName.count() * getTextRenderer().fontHeight);
		else tY = cY;
	}
	// --------------------------------------------------
	/**
	 * Returns the {@link Entity} that this {@link TEntityRendererElement}
	 * will render on the screen.
	 */
	public EntityType<?> getEntityType() { return this.livingEntity != null ? this.livingEntity.getType() : null; }
	
	/**
	 * Sets the {@link #getEntityType()}.
	 * @param entityType The new target {@link EntityType} to render.
	 */
	@SuppressWarnings("resource") //it's fine to call MinecraftClient#getInstance()
	public void setEntityType(EntityType<?> entityType)
	{
		//assign entity
		Entity entity = getCachedEntityFromType(entityType);
		this.livingEntity = (entity instanceof LivingEntity) ? (LivingEntity)entity : null;
		if(entityType == EntityType.PLAYER) this.livingEntity = getClient().player;
		
		//assign entity type name
		if(entityType != null)
			this.entityTypeName = MultilineText.create(getTextRenderer(), entityType.getName(), getTpeWidth());
		else this.entityTypeName = MultilineText.create(getTextRenderer(), TextUtils.literal("-"));
		
		//re-calculate size
		recalcCache_mobSize();
		recalcCache_cXY();
	}
	// ==================================================
	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float deltaTime)
	{
		//if the entity isn't a living one or if it isn't set
		if(this.livingEntity == null)
		{
			this.entityTypeName.drawCenterWithShadow(matrices, cX, tY);
			return;
		}
		//if the entity is set
		InventoryScreen.drawEntity(
				this.cX, this.cY, this.cache_mobSize,
				-rInt(mouseX, this.cX), -rInt(mouseY, this.cY),
				this.livingEntity);
	}
	private static int rInt(int input, int relativeTo) { return input - relativeTo; }
	// ==================================================
	public int getLivingEntityGUISize(LivingEntity e, int viewportSize)
	{
		//null check
		final int maxVal = (int) (50 * ((float)viewportSize / 80));
		if(e == null) return maxVal;
		
		//calculate default gui size
		int result = maxVal;
		{
			//return size based on entity model size
			float f1 = e.getType().getDimensions().width, f2 = e.getType().getDimensions().height;
			double d0 = Math.sqrt((f1 * f1) + (f2 * f2));
			
			//calculate and return
			if(d0 == 0) d0 = 0.1;
			result = (int) (maxVal / d0);
		}
		
		//apply any offsets
		result *= getEntityRendererSizeOffset(e.getClass());
		
		//return the result
		return result;
	}
	// ==================================================
}