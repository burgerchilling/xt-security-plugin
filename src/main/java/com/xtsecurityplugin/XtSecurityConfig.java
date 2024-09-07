package com.xtsecurityplugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;


//@ConfigGroup("example")
@ConfigGroup(XtSecurityPlugin.CONFIG_GROUP_KEY)
public interface XtSecurityConfig extends Config
{
	@ConfigItem(
		keyName = "accountid",
		name = "Enter Account ID",
		description = "Please enter login id here"
	)
	default String accountid()
	{
		return "";
	}

	@ConfigItem(
			position = 1,
			keyName = "getdata",
			name = "Get Data",
			description = "Fetch button."
	)
	default boolean loginButton()
	{
		return false;
	}


}
