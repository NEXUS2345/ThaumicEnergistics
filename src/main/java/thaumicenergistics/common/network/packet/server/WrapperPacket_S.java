package thaumicenergistics.common.network.packet.server;

import thaumicenergistics.common.network.WrapperPacket;

/**
 * Server packet wrapper.
 *
 * @author Nividica
 *
 */
public class WrapperPacket_S
	extends WrapperPacket
{
	public WrapperPacket_S()
	{

	}

	public WrapperPacket_S( final ThEServerPacket packet )
	{
		super( packet );
	}
}
