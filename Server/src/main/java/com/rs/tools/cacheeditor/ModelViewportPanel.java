package com.rs.tools.cacheeditor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.JPanel;

final class ModelViewportPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private CacheModelData model;
	private double yaw = Math.toRadians(35.0), pitch = Math.toRadians(-20.0), zoom = 1.0, panX, panY;
	private boolean wireframe;
	private Point dragOrigin;
	private int dragButton;

	ModelViewportPanel() {
		setBackground(new Color(17,20,25));
		MouseAdapter mouse = new MouseAdapter() {
			@Override public void mousePressed(MouseEvent e) { dragOrigin=e.getPoint(); dragButton=e.getButton(); requestFocusInWindow(); }
			@Override public void mouseDragged(MouseEvent e) {
				if (dragOrigin == null) return;
				int dx=e.getX()-dragOrigin.x, dy=e.getY()-dragOrigin.y;
				if (dragButton == MouseEvent.BUTTON1) {
					yaw += dx*0.012; pitch += dy*0.012; pitch=Math.max(-Math.PI*0.49,Math.min(Math.PI*0.49,pitch));
				} else { panX += dx; panY += dy; }
				dragOrigin=e.getPoint(); repaint();
			}
			@Override public void mouseReleased(MouseEvent e) { dragOrigin=null; }
			@Override public void mouseWheelMoved(MouseWheelEvent e) {
				zoom *= Math.pow(1.12,-e.getPreciseWheelRotation()); zoom=Math.max(0.15,Math.min(8.0,zoom)); repaint();
			}
		};
		addMouseListener(mouse); addMouseMotionListener(mouse); addMouseWheelListener(mouse); setFocusable(true);
	}

	void setModel(CacheModelData model) { this.model=model; resetView(); }
	void setWireframe(boolean wireframe) { this.wireframe=wireframe; repaint(); }
	void resetView() { yaw=Math.toRadians(35.0); pitch=Math.toRadians(-20.0); zoom=1.0; panX=0; panY=0; repaint(); }

	@Override protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		Graphics2D g=(Graphics2D)graphics.create();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
			paintBackdrop(g);
			if (model == null || model.getVertexCount()==0 || model.getFaceCount()==0) {
				g.setColor(CacheEditorTheme.MUTED_TEXT); g.drawString("Load a model id to preview its decoded geometry.",22,30); return;
			}
			paintModel(g);
		} finally { g.dispose(); }
	}

	private void paintBackdrop(Graphics2D g) {
		g.setColor(new Color(30,34,41)); int cx=getWidth()/2+(int)panX, cy=getHeight()/2+(int)panY;
		g.setStroke(new BasicStroke(1f)); g.drawLine(cx-18,cy,cx+18,cy); g.drawLine(cx,cy-18,cx,cy+18);
	}

	private void paintModel(Graphics2D g) {
		int[] b=model.getBounds();
		double cx=(b[0]+b[3])*0.5, cy=(b[1]+b[4])*0.5, cz=(b[2]+b[5])*0.5;
		double radius=Math.max(1.0,Math.max(b[3]-b[0],Math.max(b[4]-b[1],b[5]-b[2]))*0.5);
		double distance=radius*4.0, focal=Math.max(1.0,Math.min(getWidth(),getHeight())*0.95*zoom);
		double cosY=Math.cos(yaw), sinY=Math.sin(yaw), cosP=Math.cos(pitch), sinP=Math.sin(pitch);
		ProjectedVertex[] vertices=new ProjectedVertex[model.getVertexCount()];
		for (int i=0;i<vertices.length;i++) {
			double x=model.vertexX[i]-cx, y=-(model.vertexY[i]-cy), z=model.vertexZ[i]-cz;
			double rx=x*cosY+z*sinY, rz=z*cosY-x*sinY;
			double ry=y*cosP-rz*sinP, rzz=rz*cosP+y*sinP, depth=rzz+distance, safe=Math.max(radius*0.12,depth);
			int sx=(int)Math.round(getWidth()*0.5+panX+rx*focal/safe), sy=(int)Math.round(getHeight()*0.5+panY+ry*focal/safe);
			vertices[i]=new ProjectedVertex(rx,ry,rzz,sx,sy,depth);
		}
		List<ProjectedFace> faces=new ArrayList<ProjectedFace>(model.getFaceCount());
		for (int i=0;i<model.getFaceCount();i++) {
			if (model.faceRenderTypes[i]==2) continue;
			ProjectedVertex a=vertices[model.faceA[i]], bb=vertices[model.faceB[i]], c=vertices[model.faceC[i]];
			if (a.depth<=0 || bb.depth<=0 || c.depth<=0) continue;
			faces.add(new ProjectedFace(i,(a.depth+bb.depth+c.depth)/3.0));
		}
		Collections.sort(faces,new Comparator<ProjectedFace>() {
			@Override public int compare(ProjectedFace l,ProjectedFace r) { return Double.compare(r.depth,l.depth); }
		});
		for (ProjectedFace face:faces) {
			int i=face.index; ProjectedVertex a=vertices[model.faceA[i]], bb=vertices[model.faceB[i]], c=vertices[model.faceC[i]];
			Polygon p=new Polygon(new int[]{a.screenX,bb.screenX,c.screenX},new int[]{a.screenY,bb.screenY,c.screenY},3);
			Color base=CacheModelData.hslToRgb(model.faceColours[i]); double light=faceLighting(a,bb,c);
			int alpha=255-(model.faceAlpha[i]&0xff);
			Color shaded=new Color(scale(base.getRed(),light),scale(base.getGreen(),light),scale(base.getBlue(),light),Math.max(0,Math.min(255,alpha)));
			if (!wireframe) { g.setColor(shaded); g.fillPolygon(p); }
			if (wireframe || model.faceTextures[i]!=-1) { g.setColor(wireframe?new Color(205,214,225,210):new Color(0,0,0,45)); g.drawPolygon(p); }
		}
	}

	private double faceLighting(ProjectedVertex a,ProjectedVertex b,ProjectedVertex c) {
		double abx=b.x-a.x, aby=b.y-a.y, abz=b.z-a.z, acx=c.x-a.x, acy=c.y-a.y, acz=c.z-a.z;
		double nx=aby*acz-abz*acy, ny=abz*acx-abx*acz, nz=abx*acy-aby*acx, len=Math.sqrt(nx*nx+ny*ny+nz*nz);
		if (len<0.00001) return 0.55;
		double dot=Math.abs((nx/len)*-0.35+(ny/len)*-0.55+(nz/len)*-0.76);
		return 0.32+dot*0.68;
	}
	private int scale(int v,double f) { return Math.max(0,Math.min(255,(int)Math.round(v*f))); }

	private static final class ProjectedVertex {
		final double x,y,z,depth; final int screenX,screenY;
		ProjectedVertex(double x,double y,double z,int screenX,int screenY,double depth) { this.x=x;this.y=y;this.z=z;this.screenX=screenX;this.screenY=screenY;this.depth=depth; }
	}
	private static final class ProjectedFace {
		final int index; final double depth;
		ProjectedFace(int index,double depth) { this.index=index;this.depth=depth; }
	}
}
