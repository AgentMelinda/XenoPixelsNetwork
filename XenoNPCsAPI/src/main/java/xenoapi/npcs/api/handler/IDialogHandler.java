package xenoapi.npcs.api.handler;

import java.util.List;

import xenoapi.npcs.api.handler.data.IDialog;
import xenoapi.npcs.api.handler.data.IDialogCategory;

public interface IDialogHandler {
	
	public List<IDialogCategory> categories();
	
	public IDialog get(int id);
}
