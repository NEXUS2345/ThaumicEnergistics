package thaumicenergistics.common.blocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import thaumicenergistics.client.textures.BlockTextureManager;
import thaumicenergistics.common.registries.Renderers;
import thaumicenergistics.common.tiles.TileInfusionProvider;

/**
 * {@link TileInfusionProvider} block.
 *
 * @author Nividica
 *
 */
public class BlockInfusionProvider
	extends AbstractBlockProviderBase
{

	public BlockInfusionProvider()
	{
		// Call super with material machine (iron)
		super( Material.iron );

		// Basic hardness
		this.setHardness( 1.0f );

		// Sound of metal
		this.setStepSound( Block.soundTypeMetal );
	}

	@Override
	public TileEntity createNewTileEntity( final World world, final int metaData )
	{
		// Create a new provider tile, passing the side to attach to
		TileInfusionProvider tile = new TileInfusionProvider();

		// Setup the infusion provider
		tile.setupProvider( metaData );

		// Return the tile
		return tile;
	}

	@SideOnly(Side.CLIENT)
	@Override
	public IIcon getIcon( final int side, final int metaData )
	{
		return BlockTextureManager.INFUSION_PROVIDER.getTextures()[1];
	}

	@SideOnly(Side.CLIENT)
	@Override
	public int getRenderType()
	{
		// Provide our custom ID
		return Renderers.InfusionProviderRenderID;
	}

	@Override
	public String getUnlocalizedName()
	{
		return BlockEnum.INFUSION_PROVIDER.getUnlocalizedName();
	}

	@Override
	public void onNeighborBlockChange( final World world, final int x, final int y, final int z, final Block neighbor )
	{
		// Get tile entity
		TileEntity tileProvider = world.getTileEntity( x, y, z );
		if( tileProvider instanceof TileInfusionProvider )
		{
			// Inform our tile entity a neighbor has changed
			( (TileInfusionProvider)tileProvider ).checkGridConnectionColor();
		}

	}

}
