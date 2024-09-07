package com.xtsecurityplugin;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class XtSecurityPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(XtSecurityPlugin.class);
		RuneLite.main(args);
	}
}