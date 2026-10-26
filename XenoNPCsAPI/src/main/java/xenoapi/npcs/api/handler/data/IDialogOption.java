package xenoapi.npcs.api.handler.data;

public interface IDialogOption {
	int getSlot();
	
	String getName();
	IDialogOption setName(String name);

	String getText();
	IDialogOption setText(String text);

	/**
	 *
	 * @return see OptionType
	 */
	int getType();
	IDialogOption setType(int type);

	String[] getCommands();
	void setCommands(String... commands);

	IDialog getDialog();
	IDialogOption setDialog(IDialog dialog);

	/** Native DMZ NPC giver id; opens DMZ's own available quest menu. */
	default String getDmzQuestNpc() { return ""; }
	default IDialogOption setDmzQuestNpc(String npcId) {
		throw new UnsupportedOperationException("Native DMZ quest options are unavailable");
	}

	/** Xeno quest id, with progress owned separately from DMZ quests. */
	default String getXenoQuest() { return ""; }
	default IDialogOption setXenoQuest(String questId) {
		throw new UnsupportedOperationException("Xeno quest options are unavailable");
	}

}
