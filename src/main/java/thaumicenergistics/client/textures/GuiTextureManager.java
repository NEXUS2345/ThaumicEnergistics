package thaumicenergistics.client.textures;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.ResourceLocation;
import thaumicenergistics.common.ThaumicEnergistics;

/**
 * Textures for all ThE GUIs.
 *
 * @author Nividica
 *
 */
@SideOnly(Side.CLIENT)
public enum GuiTextureManager
{
		ESSENTIA_LEVEL_EMITTER ("essentia.level.emitter"),
		ESSENTIA_STORAGE_BUS ("essentia.storage.bus"),
		ESSENTIA_TERMINAL ("essentia.terminal"),
		ESSENTIA_IO_BUS ("essentia.io.bus"),
		ARCANE_CRAFTING_TERMINAL ("arcane.crafting"),
		PRIORITY ("priority"),
		CELL_WORKBENCH ("essentia.cell.workbench"),
		ARCANE_ASSEMBLER ("arcane.assembler"),
		KNOWLEDGE_INSCRIBER ("knowledge.inscriber"),
		ESSENTIA_VIBRATION_CHAMBER ("essentia.vibration.chamber"),
		DISTILLATION_ENCODER ("distillation.encoder");

	private ResourceLocation texture;

	private GuiTextureManager( final String textureName )
	{
		// Create the resource location
		this.texture = new ResourceLocation( ThaumicEnergistics.MOD_ID, "textures/gui/" + textureName + ".png" );
	}

	public ResourceLocation getTexture()
	{
		return this.texture;
	}

}
