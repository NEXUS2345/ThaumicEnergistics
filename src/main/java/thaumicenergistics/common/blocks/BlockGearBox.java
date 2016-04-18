package thaumicenergistics.common.blocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.IIcon;
import thaumicenergistics.client.textures.BlockTextureManager;
import thaumicenergistics.common.tiles.TileGearBox;

/**
 * {@link TileGearBox} block, iron form.
 *
 * @author Nividica
 *
 */
public class BlockGearBox
	extends AbstractBlockGearBoxBase
{
	@SideOnly(Side.CLIENT)
	@Override
	public IIcon getIcon( final int side, final int meta )
	{
		return BlockTextureManager.GEAR_BOX.getTextures()[0];
	}

	@Override
	public String getUnlocalizedName()
	{
		return BlockEnum.IRON_GEAR_BOX.getUnlocalizedName();
	}

}
