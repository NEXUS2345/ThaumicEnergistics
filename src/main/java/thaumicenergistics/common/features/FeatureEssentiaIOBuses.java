package thaumicenergistics.common.features;

import java.util.ArrayList;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import net.minecraft.item.ItemStack;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchPage;
import thaumicenergistics.api.IThEConfig;
import thaumicenergistics.api.ThEApi;
import thaumicenergistics.common.parts.AEPartsEnum;
import thaumicenergistics.common.parts.PartEssentiaExportBus;
import thaumicenergistics.common.parts.PartEssentiaImportBus;
import thaumicenergistics.common.parts.PartEssentiaStorageBus;
import thaumicenergistics.common.registries.FeatureRegistry;
import thaumicenergistics.common.registries.RecipeRegistry;
import thaumicenergistics.common.registries.ResearchRegistry;
import thaumicenergistics.common.registries.ResearchRegistry.PseudoResearchTypes;
import thaumicenergistics.common.registries.ResearchRegistry.ResearchTypes;

/**
 * {@link PartEssentiaImportBus}, {@link PartEssentiaExportBus}, and {@link PartEssentiaStorageBus} feature.
 *
 * @author Nividica
 *
 */
public class FeatureEssentiaIOBuses
	extends ThEThaumcraftResearchFeature
{

	private boolean isImportExportEnabled = false;

	public FeatureEssentiaIOBuses()
	{
		super( ResearchTypes.IO.getKey() );
	}

	@Override
	protected boolean checkConfigs( final IThEConfig theConfig )
	{
		this.isImportExportEnabled = theConfig.craftIOBuses() && ( AEConfig.instance.isFeatureEnabled( AEFeature.ImportBus ) || AEConfig.instance
						.isFeatureEnabled( AEFeature.ExportBus ) );

		return true;
	}

	@Override
	protected Object[] getItemReqs( final CommonDependantItems cdi )
	{
		return null;
	}

	@Override
	protected ThEThaumcraftResearchFeature getParentFeature()
	{
		return FeatureRegistry.instance().featureConversionCores;
	}

	@Override
	protected void registerCrafting( final CommonDependantItems cdi )
	{
		// My items
		ItemStack DiffusionCore = ThEApi.instance().items().DiffusionCore.getStack();
		ItemStack CoalescenceCore = ThEApi.instance().items().CoalescenceCore.getStack();
		ItemStack EssentiaStorageBus = ThEApi.instance().parts().Essentia_StorageBus.getStack();

		// Set Storage Bus aspects
		AspectList storageAspectList = new AspectList();
		storageAspectList.add( Aspect.FIRE, 3 );
		storageAspectList.add( Aspect.EARTH, 3 );
		storageAspectList.add( Aspect.WATER, 1 );

		// Storage Bus recipe
		Object[] recipeStorageBus = new Object[] { true, "DFC", "IWI", 'D', DiffusionCore, 'C', CoalescenceCore, 'I', cdi.IronIngot, 'F',
						cdi.FilterTube, 'W', cdi.WardedGlass };

		// Register the storage bus
		RecipeRegistry.PART_STORAGE_BUS = ThaumcraftApi.addArcaneCraftingRecipe( this.researchKey, EssentiaStorageBus,
			storageAspectList, recipeStorageBus );

		// Is import and export enabled?
		if( this.isImportExportEnabled )
		{
			// My items
			ItemStack EssentiaImportBus = ThEApi.instance().parts().Essentia_ImportBus.getStack();
			ItemStack EssentiaExportBus = ThEApi.instance().parts().Essentia_ExportBus.getStack();

			// Set IO Bus aspects
			AspectList ioAspectList = new AspectList();
			ioAspectList.add( Aspect.FIRE, 2 );
			ioAspectList.add( Aspect.EARTH, 2 );
			ioAspectList.add( Aspect.WATER, 1 );

			// Import Bus recipe
			Object[] recipeImportBus = new Object[] { "JDJ", "IFI", 'J', cdi.WardedJar, 'D', DiffusionCore, 'I', cdi.IronIngot, 'F', cdi.FilterTube };

			// Export Bus recipe
			Object[] recipeExportBus = new Object[] { "JCJ", "IFI", 'J', cdi.WardedJar, 'C', CoalescenceCore, 'I', cdi.IronIngot, 'F',
							cdi.FilterTube };

			// Register Import Bus
			RecipeRegistry.PART_IMPORT_BUS = ThaumcraftApi.addArcaneCraftingRecipe( this.researchKey, EssentiaImportBus,
				ioAspectList, recipeImportBus );

			// Register Export Bus
			RecipeRegistry.PART_EXPORT_BUS = ThaumcraftApi.addArcaneCraftingRecipe( this.researchKey, EssentiaExportBus,
				ioAspectList, recipeExportBus );
		}
	}

	@Override
	protected void registerPseudoParents()
	{
		PseudoResearchTypes.TUBEFILTER.registerPsudeoResearch();
	}

	@Override
	protected void registerResearch()
	{
		// Set the research aspects
		AspectList ioAspectList = new AspectList();
		ioAspectList.add( Aspect.MECHANISM, 5 );
		ioAspectList.add( Aspect.METAL, 3 );
		ioAspectList.add( Aspect.CRYSTAL, 3 );
		ioAspectList.add( Aspect.AIR, 3 );

		// Set the icon
		ItemStack ioIcon = AEPartsEnum.EssentiaExportBus.getStack();

		ArrayList<ResearchPage> pageList = new ArrayList<ResearchPage>();

		// Info pages
		if( this.isImportExportEnabled )
		{
			pageList.add( new ResearchPage( ResearchTypes.IO.getPageName( 1 ) ) );
		}
		pageList.add( new ResearchPage( ResearchTypes.IO.getPageName( 2 ) ) );

		// Recipe pages
		if( this.isImportExportEnabled )
		{
			pageList.add( new ResearchPage( RecipeRegistry.PART_IMPORT_BUS ) );
			pageList.add( new ResearchPage( RecipeRegistry.PART_EXPORT_BUS ) );
		}

		pageList.add( new ResearchPage( RecipeRegistry.PART_STORAGE_BUS ) );

		// Set the pages
		ResearchPage[] ioPages = pageList.toArray( new ResearchPage[pageList.size()] );

		// Create the IO research
		ResearchTypes.IO.createResearchItem( ioAspectList, ResearchRegistry.COMPLEXITY_MEDIUM, ioIcon, ioPages );
		ResearchTypes.IO.researchItem.setParents( this.getFirstValidParentKey( false ), PseudoResearchTypes.TUBEFILTER.getKey() );
		ResearchTypes.IO.researchItem.setParentsHidden( "TUBEFILTER" );
		ResearchTypes.IO.researchItem.setConcealed();
		ResearchTypes.IO.researchItem.registerResearchItem();
	}
}
