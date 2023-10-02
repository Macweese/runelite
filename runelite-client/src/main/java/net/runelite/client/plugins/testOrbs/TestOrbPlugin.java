package net.runelite.client.plugins.testOrbs;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@PluginDescriptor(
		name = "Block orbs TEST",
		description = "Prevent clicks from happening when clicking the minimap orbs.",
		tags = {"minimap", "status", "orb", "click", "walk", "here"}
)
public class TestOrbPlugin extends Plugin
{
	@Inject
	private TestOrbConfig config;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	// Enum 906 contains a map of all the item ids and
	// the amount of special attack energy they require
	// (since rev 217.1)
	private static final int SPECIAL_ATTACK_ITEM_MAP = 906;

	private static final int UPDATE_HITPOINTS_ORB_SCRIPT_ID = 446;
	private static final int UPDATE_SPEC_ORB_SCRIPT_ID = 2792;

	private static final int HITPOINTS_ORB_CLICKABLE_WIDGET_CHILD_ID = 8;
	private static final int SPEC_ORB_CLICKABLE_WIDGET_CHILD_ID = 35;

	private Widget specOrbWidget;
	private Widget hitpointsOrbWidget;

	private boolean consumeSpecOrb;
	private boolean consumeHitpointsOrb;

	@Provides
	TestOrbConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TestOrbConfig.class);
	}

	@Override
	public void startUp()
	{
		consumeSpecOrb = config.blockSpecialAttackOrb();
		consumeHitpointsOrb = config.blockHitpointsOrb();

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			hitpointsOrbWidget = client.getWidget(WidgetID.MINIMAP_GROUP_ID, HITPOINTS_ORB_CLICKABLE_WIDGET_CHILD_ID);
			specOrbWidget = client.getWidget(WidgetID.MINIMAP_GROUP_ID, SPEC_ORB_CLICKABLE_WIDGET_CHILD_ID);

			if (!client.isResized())
			{
				return;
			}

			setSpecOrbConsuming(consumeSpecOrb);
			setHitpointsOrbConsuming(consumeHitpointsOrb);
		});
	}

	@Override
	public void shutDown()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			setSpecOrbConsuming(false);
			setHitpointsOrbConsuming(false);
		});
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equalsIgnoreCase("testorbs"))
		{
			return;
		}

		consumeSpecOrb = config.blockSpecialAttackOrb();
		consumeHitpointsOrb = config.blockHitpointsOrb();

		clientThread.invokeLater(() ->
		{
			if (!client.isResized())
			{
				return;
			}

			setSpecOrbConsuming(consumeSpecOrb);
			setHitpointsOrbConsuming(consumeHitpointsOrb);
		});
	}

	@Subscribe
	private void onScriptPostFired(ScriptPostFired event)
	{
		if (!client.isResized())
		{
			return;
		}

		if (event.getScriptId() == UPDATE_HITPOINTS_ORB_SCRIPT_ID)
		{
			if (hitpointsOrbWidget == null)
			{
				hitpointsOrbWidget = client.getWidget(WidgetID.MINIMAP_GROUP_ID, HITPOINTS_ORB_CLICKABLE_WIDGET_CHILD_ID);
			}

			boolean permeable = hitpointsOrbWidget.isHidden() || !hitpointsOrbWidget.getNoClickThrough();

			if (permeable)
			{
				setHitpointsOrbConsuming(consumeHitpointsOrb);
			}
		}

		if (event.getScriptId() == UPDATE_SPEC_ORB_SCRIPT_ID)
		{
			if (specOrbWidget == null)
			{
				specOrbWidget = client.getWidget(WidgetID.MINIMAP_GROUP_ID, SPEC_ORB_CLICKABLE_WIDGET_CHILD_ID);
			}

			boolean permeable = specOrbWidget.isHidden() || !specOrbWidget.getNoClickThrough();

			if (permeable)
			{
				setSpecOrbConsuming(consumeSpecOrb);
			}
		}
	}

	private boolean hasSpecialAttackItem()
	{
		final Item[] items = client.getItemContainer(InventoryID.EQUIPMENT).getItems();

		return Arrays.stream(items)
				.dropWhile(Objects::isNull)
				.mapToInt(Item::getId)
				.anyMatch(i -> Arrays.stream(client.getEnum(SPECIAL_ATTACK_ITEM_MAP).getKeys()).anyMatch(j -> i == j));
	}

	private boolean isDebilitated()
	{
		return client.getVarpValue(VarPlayer.DISEASE_VALUE) > 0
				|| client.getVarpValue(VarPlayer.POISON) > 0
				|| client.getVarbitValue(Varbits.PARASITE) > 0;
	}

	private void setSpecOrbConsuming(boolean consume)
	{
		if (specOrbWidget == null)
		{
			return;
		}

		if (consume)
		{
			specOrbWidget.setNoClickThrough(consumeSpecOrb);
			specOrbWidget.setHidden(false);
		}
		else
		{
			specOrbWidget.setHidden(!hasSpecialAttackItem());
		}
	}

	private void setHitpointsOrbConsuming(boolean consume)
	{
		if (hitpointsOrbWidget == null)
		{
			return;
		}

		boolean isPoisonedOrDiseased = isDebilitated();

		if (consume)
		{
			hitpointsOrbWidget.setNoClickThrough(consumeHitpointsOrb);
			hitpointsOrbWidget.setHidden(false);
		}
		else
		{
			hitpointsOrbWidget.setNoClickThrough(isPoisonedOrDiseased);
			hitpointsOrbWidget.setHidden(!isPoisonedOrDiseased);
		}
	}

	private void setOrbConsuming(Widget widget, boolean consume)
	{
		if (widget == null)
		{
			return;
		}

		boolean isConsuming = false;

		if (widget.getId() == HITPOINTS_ORB_CLICKABLE_WIDGET_CHILD_ID)
		{
			isConsuming = isDebilitated();
		}
		else if (widget.getId() == SPEC_ORB_CLICKABLE_WIDGET_CHILD_ID)
		{
			isConsuming = hasSpecialAttackItem();
		}

		if (consume)
		{
			widget.setNoClickThrough(consumeHitpointsOrb);
			widget.setHidden(false);
		}
		else
		{
			widget.setNoClickThrough(isConsuming);
			widget.setHidden(!isConsuming);
		}
	}

	private void resized()
	{
		client.isResized();
	}
}