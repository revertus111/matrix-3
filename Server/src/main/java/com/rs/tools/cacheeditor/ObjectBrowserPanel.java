package com.rs.tools.cacheeditor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.rs.cache.loaders.ObjectDefinitions;

final class ObjectBrowserPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private final CacheSession session;
	private final JTextField searchField=new JTextField();
	private final JLabel title=new JLabel("No object loaded"), idValue=new JLabel("-"), sizeValue=new JLabel("-"), animationValue=new JLabel("-"), clipValue=new JLabel("-");
	private final JTextArea modelArea=new JTextArea(), optionArea=new JTextArea();
	private final JPanel modelButtons=new JPanel(new FlowLayout(FlowLayout.LEFT,6,4));
	private final JLabel status=new JLabel("Enter an object ID or exact object name.");
	private int loadedObjectId=-1;

	ObjectBrowserPanel(CacheSession session) {
		super(new BorderLayout(12,12)); this.session=session;
		setBackground(CacheEditorTheme.PANEL); setBorder(CacheEditorTheme.panelPadding(16,16,16,16));
		add(createToolbar(),BorderLayout.NORTH); add(createDetails(),BorderLayout.CENTER);
	}

	private JPanel createToolbar() {
		JPanel bar=new JPanel(new BorderLayout(8,0)); bar.setBackground(CacheEditorTheme.PANEL);
		JPanel left=new JPanel(new FlowLayout(FlowLayout.LEFT,7,0)); left.setBackground(CacheEditorTheme.PANEL);
		JLabel label=new JLabel("Object ID / Name"); label.setForeground(CacheEditorTheme.TEXT); left.add(label);
		searchField.setPreferredSize(new Dimension(220,35)); CacheEditorTheme.styleTextField(searchField); searchField.addActionListener(e->loadSearch()); left.add(searchField);
		JButton load=new JButton("Load"); CacheEditorTheme.styleButton(load); load.addActionListener(e->loadSearch()); left.add(load);
		JButton prev=new JButton("< Prev"); CacheEditorTheme.styleButton(prev); prev.addActionListener(e->step(-1)); left.add(prev);
		JButton next=new JButton("Next >"); CacheEditorTheme.styleButton(next); next.addActionListener(e->step(1)); left.add(next);
		bar.add(left,BorderLayout.WEST); status.setForeground(CacheEditorTheme.MUTED_TEXT); status.setHorizontalAlignment(JLabel.RIGHT); bar.add(status,BorderLayout.CENTER); return bar;
	}

	private JPanel createDetails() {
		JPanel root=new JPanel(new BorderLayout(12,12)); root.setBackground(CacheEditorTheme.PANEL);
		JPanel summary=new JPanel(new GridLayout(0,2,8,8)); summary.setBackground(CacheEditorTheme.CARD); summary.setBorder(CacheEditorTheme.cardBorder());
		title.setFont(CacheEditorTheme.TITLE_FONT); title.setForeground(CacheEditorTheme.TEXT);
		addPair(summary,"Object",title); addPair(summary,"ID",idValue); addPair(summary,"Size",sizeValue); addPair(summary,"Animation",animationValue); addPair(summary,"Clip",clipValue);
		root.add(summary,BorderLayout.NORTH);

		JPanel center=new JPanel(new GridLayout(1,2,12,0)); center.setBackground(CacheEditorTheme.PANEL);
		center.add(createTextCard("MODEL REFERENCES",modelArea)); center.add(createTextCard("OPTIONS",optionArea)); root.add(center,BorderLayout.CENTER);

		JPanel models=new JPanel(new BorderLayout()); models.setBackground(CacheEditorTheme.CARD); models.setBorder(CacheEditorTheme.cardBorder());
		JLabel h=new JLabel("OPEN REFERENCED MODEL"); h.setFont(CacheEditorTheme.SECTION_FONT); h.setForeground(CacheEditorTheme.TEXT); models.add(h,BorderLayout.NORTH);
		modelButtons.setOpaque(false); models.add(modelButtons,BorderLayout.CENTER); root.add(models,BorderLayout.SOUTH);
		return root;
	}

	private JPanel createTextCard(String heading,JTextArea area) {
		JPanel card=new JPanel(new BorderLayout(0,8)); card.setBackground(CacheEditorTheme.CARD); card.setBorder(CacheEditorTheme.cardBorder());
		JLabel h=new JLabel(heading); h.setFont(CacheEditorTheme.SECTION_FONT); h.setForeground(CacheEditorTheme.TEXT); card.add(h,BorderLayout.NORTH);
		area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true); area.setBackground(CacheEditorTheme.WINDOW); area.setForeground(CacheEditorTheme.TEXT); area.setFont(CacheEditorTheme.BODY_FONT);
		card.add(new JScrollPane(area),BorderLayout.CENTER); return card;
	}

	private void addPair(JPanel panel,String name,JLabel value) {
		JLabel n=new JLabel(name); n.setForeground(CacheEditorTheme.MUTED_TEXT); n.setFont(CacheEditorTheme.SMALL_FONT); value.setForeground(CacheEditorTheme.TEXT); value.setFont(CacheEditorTheme.BODY_FONT); panel.add(n); panel.add(value);
	}

	private void loadSearch() {
		String query=searchField.getText().trim();
		if (query.length()==0) return;
		try { loadObject(Integer.parseInt(query)); return; } catch (NumberFormatException ignored) { }
		status.setText("Searching exact name...");
		for (int id=0; id<=65535; id++) {
			ObjectDefinitions def=session.getObjectDefinition(id);
			if (def!=null && def.name!=null && query.equalsIgnoreCase(def.name)) { loadObject(id); return; }
		}
		showError("No exact object name match found in the first 65,536 object IDs. Use the numeric object ID for direct lookup.");
	}

	private void step(int delta) {
		int base=loadedObjectId>=0?loadedObjectId:0;
		int id=Math.max(0,base+delta);
		loadObject(id);
	}

	private void loadObject(int id) {
		try {
			ObjectDefinitions def=session.getObjectDefinition(id); loadedObjectId=id; searchField.setText(Integer.toString(id));
			title.setText(def.name==null?"null":def.name); idValue.setText(Integer.toString(id)); sizeValue.setText(def.sizeX+" x "+def.sizeY);
			animationValue.setText(Integer.toString(def.objectAnimation)); clipValue.setText(def.clipType+" / projectile="+def.projectileCliped);
			modelArea.setText(formatModels(def.modelIds)); optionArea.setText(formatOptions(def)); rebuildModelButtons(def.modelIds);
			status.setText("Object "+id+" loaded.");
		} catch (Exception ex) { showError("Unable to load object "+id+":\n"+ex.getMessage()); }
	}

	private String formatModels(int[][] ids) {
		if (ids==null || ids.length==0) return "No model references.";
		StringBuilder out=new StringBuilder();
		for (int group=0;group<ids.length;group++) {
			out.append("Group ").append(group).append(": ");
			for (int i=0;i<ids[group].length;i++) { if (i>0) out.append(", "); out.append(ids[group][i]); }
			out.append('\n');
		}
		return out.toString();
	}

	private String formatOptions(ObjectDefinitions def) {
		StringBuilder out=new StringBuilder();
		for (int i=1;i<=5;i++) { String option=def.getOption(i); if (option!=null && option.length()>0) out.append(i).append(": ").append(option).append('\n'); }
		return out.length()==0?"No options.":out.toString();
	}

	private void rebuildModelButtons(int[][] ids) {
		modelButtons.removeAll(); List<Integer> unique=new ArrayList<Integer>();
		if (ids!=null) for (int[] group:ids) if (group!=null) for (int id:group) if (!unique.contains(id)) unique.add(id);
		for (final Integer id:unique) {
			JButton button=new JButton("Model "+id); CacheEditorTheme.styleButton(button);
			button.setToolTipText("Referenced model id "+id+". Use this id in Assets > Models to preview/export it.");
			button.addActionListener(e->{ java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(Integer.toString(id)),null); status.setText("Copied model "+id+" to clipboard. Open Assets > Models."); });
			modelButtons.add(button);
		}
		modelButtons.revalidate(); modelButtons.repaint();
	}

	private void showError(String message) { JOptionPane.showMessageDialog(this,message,"Object Browser",JOptionPane.ERROR_MESSAGE); }
}
