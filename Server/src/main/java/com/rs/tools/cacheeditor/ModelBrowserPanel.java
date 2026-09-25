package com.rs.tools.cacheeditor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

final class ModelBrowserPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private final CacheSession session;
	private final ModelViewportPanel viewport=new ModelViewportPanel();
	private final JTextField modelIdField=new JTextField();
	private final JLabel status=new JLabel("Enter a model id.");
	private final JLabel vertices=new JLabel("-"), faces=new JLabel("-"), texturedFaces=new JLabel("-"), version=new JLabel("-"), bounds=new JLabel("-");
	private final JButton loadButton=new JButton("Load"), rawExportButton=new JButton("Export Raw"), objExportButton=new JButton("Export OBJ"), resetViewButton=new JButton("Reset View");
	private final JCheckBox wireframe=new JCheckBox("Wireframe");
	private CacheModelData loadedModel;
	private int loadedModelId=-1;

	ModelBrowserPanel(CacheSession session) {
		super(new BorderLayout(12,12)); this.session=session;
		setBackground(CacheEditorTheme.PANEL); setBorder(CacheEditorTheme.panelPadding(16,16,16,16));
		add(createToolbar(),BorderLayout.NORTH); add(viewport,BorderLayout.CENTER); add(createInspector(),BorderLayout.EAST); setLoadedState(false);
	}

	private JPanel createToolbar() {
		JPanel toolbar=new JPanel(new BorderLayout(10,0)); toolbar.setBackground(CacheEditorTheme.PANEL);
		JPanel left=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0)); left.setBackground(CacheEditorTheme.PANEL);
		JLabel label=new JLabel("Model ID"); label.setForeground(CacheEditorTheme.TEXT); left.add(label);
		modelIdField.setPreferredSize(new Dimension(115,35)); CacheEditorTheme.styleTextField(modelIdField); modelIdField.addActionListener(e->loadModel()); left.add(modelIdField);
		CacheEditorTheme.styleButton(loadButton); loadButton.addActionListener(e->loadModel()); left.add(loadButton);
		CacheEditorTheme.styleButton(rawExportButton); rawExportButton.addActionListener(e->exportRaw()); left.add(rawExportButton);
		CacheEditorTheme.styleButton(objExportButton); objExportButton.addActionListener(e->exportObj()); left.add(objExportButton);
		CacheEditorTheme.styleButton(resetViewButton); resetViewButton.addActionListener(e->viewport.resetView()); left.add(resetViewButton);
		wireframe.setOpaque(false); wireframe.setForeground(CacheEditorTheme.TEXT); wireframe.addActionListener(e->viewport.setWireframe(wireframe.isSelected())); left.add(wireframe);
		toolbar.add(left,BorderLayout.WEST); status.setForeground(CacheEditorTheme.MUTED_TEXT); status.setHorizontalAlignment(JLabel.RIGHT); toolbar.add(status,BorderLayout.EAST);
		return toolbar;
	}

	private JPanel createInspector() {
		JPanel inspector=new JPanel(new BorderLayout()); inspector.setBackground(CacheEditorTheme.CARD); inspector.setPreferredSize(new Dimension(245,0)); inspector.setBorder(CacheEditorTheme.cardBorder());
		JLabel heading=new JLabel("MODEL"); heading.setFont(CacheEditorTheme.SECTION_FONT); heading.setForeground(CacheEditorTheme.TEXT); inspector.add(heading,BorderLayout.NORTH);
		JPanel values=new JPanel(new GridLayout(0,1,0,7)); values.setOpaque(false); values.setBorder(BorderFactory.createEmptyBorder(14,0,0,0));
		addValue(values,"Format version",version); addValue(values,"Vertices",vertices); addValue(values,"Faces",faces); addValue(values,"Textured faces",texturedFaces); addValue(values,"Bounds",bounds); inspector.add(values,BorderLayout.CENTER);
		JLabel help=new JLabel("<html><b>Controls</b><br>Left drag: orbit<br>Middle/right drag: pan<br>Wheel: zoom<br><br>OBJ exports geometry + face-colour materials. Texture ids are preserved as comments.</html>");
		help.setFont(CacheEditorTheme.SMALL_FONT); help.setForeground(CacheEditorTheme.MUTED_TEXT); inspector.add(help,BorderLayout.SOUTH); return inspector;
	}

	private void addValue(JPanel panel,String name,JLabel value) {
		JLabel n=new JLabel(name); n.setFont(CacheEditorTheme.SMALL_FONT); n.setForeground(CacheEditorTheme.MUTED_TEXT);
		value.setFont(CacheEditorTheme.BODY_FONT); value.setForeground(CacheEditorTheme.TEXT); panel.add(n); panel.add(value);
	}

	private void loadModel() {
		final int modelId;
		try { modelId=Integer.parseInt(modelIdField.getText().trim()); if (modelId<0) throw new NumberFormatException(); }
		catch (NumberFormatException ex) { showError("Enter a valid non-negative model id."); return; }
		setBusy(true); status.setText("Loading model "+modelId+"...");
		new SwingWorker<CacheModelData,Void>() {
			@Override protected CacheModelData doInBackground() {
				byte[] bytes=session.readModelBytes(modelId);
				if (bytes==null || bytes.length==0) throw new IllegalArgumentException("No model bytes found at cache index "+CacheSession.MODEL_INDEX+", archive "+modelId+", file 0.");
				return CacheModelData.decode(bytes);
			}
			@Override protected void done() {
				try {
					loadedModel=get(); loadedModelId=modelId; viewport.setModel(loadedModel); updateInspector(); setLoadedState(true);
					status.setText("Model "+modelId+" loaded. Last model archive: "+session.getLastModelId());
				} catch (Exception ex) {
					loadedModel=null; loadedModelId=-1; viewport.setModel(null); clearInspector(); setLoadedState(false);
					Throwable cause=ex.getCause()==null?ex:ex.getCause(); showError("Unable to load model "+modelId+":\n"+cause.getMessage()); status.setText("Load failed.");
				} finally { setBusy(false); }
			}
		}.execute();
	}

	private void updateInspector() {
		int[] b=loadedModel.getBounds(); version.setText(Integer.toString(loadedModel.version)); vertices.setText(Integer.toString(loadedModel.getVertexCount()));
		faces.setText(Integer.toString(loadedModel.getFaceCount())); texturedFaces.setText(Integer.toString(loadedModel.getTexturedFaceCount()));
		bounds.setText("<html>X "+b[0]+".."+b[3]+"<br>Y "+b[1]+".."+b[4]+"<br>Z "+b[2]+".."+b[5]+"</html>");
	}
	private void clearInspector() { version.setText("-"); vertices.setText("-"); faces.setText("-"); texturedFaces.setText("-"); bounds.setText("-"); }

	private void exportRaw() {
		if (loadedModel==null) return;
		JFileChooser chooser=new JFileChooser(); chooser.setDialogTitle("Export raw model "+loadedModelId); chooser.setSelectedFile(new File("model_"+loadedModelId+".dat"));
		if (chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION) return;
		try { loadedModel.exportRaw(chooser.getSelectedFile()); status.setText("Raw model exported."); } catch (Exception ex) { showError("Raw export failed:\n"+ex.getMessage()); }
	}

	private void exportObj() {
		if (loadedModel==null) return;
		JFileChooser chooser=new JFileChooser(); chooser.setDialogTitle("Export OBJ model "+loadedModelId); chooser.setFileFilter(new FileNameExtensionFilter("Wavefront OBJ","obj")); chooser.setSelectedFile(new File("model_"+loadedModelId+".obj"));
		if (chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION) return;
		File target=ensureExtension(chooser.getSelectedFile(),".obj");
		try { loadedModel.exportObj(target); status.setText("OBJ + MTL exported."); } catch (Exception ex) { showError("OBJ export failed:\n"+ex.getMessage()); }
	}

	private File ensureExtension(File file,String ext) {
		if (file.getName().toLowerCase().endsWith(ext)) return file;
		File parent=file.getAbsoluteFile().getParentFile(); return new File(parent,file.getName()+ext);
	}
	private void setBusy(boolean busy) { loadButton.setEnabled(!busy); modelIdField.setEnabled(!busy); }
	private void setLoadedState(boolean loaded) { rawExportButton.setEnabled(loaded); objExportButton.setEnabled(loaded); resetViewButton.setEnabled(loaded); wireframe.setEnabled(loaded); }
	private void showError(String message) { JOptionPane.showMessageDialog(this,message,"Model Viewer",JOptionPane.ERROR_MESSAGE); }
}
