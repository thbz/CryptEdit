package org.thbz.CryptEdit;

/*
Source d'inspiration : http:://forum.codecall.net/topic/49721-simple-text-editor/
*/

import java.awt.Font;
import java.awt.Toolkit;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.Charset;
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.util.Date;
import javax.swing.Action;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.text.DefaultEditorKit;

class CryptEdit extends JFrame {
    final static private String newFileTitle = "Untitled";
    private JTextArea area = new JTextArea(20, 80);
    private JTextField status = new JTextField(80);
    private JFileChooser dialog =
	new JFileChooser(System.getProperty("user.dir"));

    final static private class FileInfo {
	String name;
	String password;
	boolean changed;

	private FileInfo(String name, String password, boolean changed) {
	    this.name = name;
	    this.password = password;
	    this.changed = changed;
	}

	static FileInfo uninitialized() {
	    return new FileInfo(newFileTitle, null, false);
	}

	static FileInfo newFromFile(String fileName, String password) {
	    return new FileInfo(fileName, password, false);
	}
    };
    
    FileInfo currentFile = FileInfo.uninitialized();

    private void setAccelerator(int key, Action action) {
	action.putValue(Action.ACCELERATOR_KEY,
		     KeyStroke.getKeyStroke(key,
					    ActionEvent.CTRL_MASK));
    }
    
    public CryptEdit(String[] arg) {
	String fileToOpen;
	if(arg.length > 0)
		fileToOpen = arg[0];
	else
		fileToOpen = null;
	    
	PBE.init();

	setAccelerator(KeyEvent.VK_C, Copy);
	setAccelerator(KeyEvent.VK_N, New);
	setAccelerator(KeyEvent.VK_O, Open);
	setAccelerator(KeyEvent.VK_P, SetPassword);
	setAccelerator(KeyEvent.VK_Q, Quit);
	setAccelerator(KeyEvent.VK_S, Save);
	setAccelerator(KeyEvent.VK_V, Paste);
	setAccelerator(KeyEvent.VK_W, OpenWithPassword);
	setAccelerator(KeyEvent.VK_X, Cut);

	area.setFont(new Font("Monospaced", Font.PLAIN, 12));
	JScrollPane scroll =
	    new JScrollPane(area, 
			    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
			    JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
	add(scroll, BorderLayout.CENTER);
		
	JMenuBar JMB = new JMenuBar();
	setJMenuBar(JMB);

	JMenu fileMenu = new JMenu("File");
	fileMenu.setMnemonic(KeyEvent.VK_F);
	JMenu editMenu = new JMenu("Edit");
	editMenu.setMnemonic(KeyEvent.VK_E);
	JMB.add(fileMenu); 
	JMB.add(editMenu);
		
	fileMenu.add(New);
	fileMenu.add(Open);
	fileMenu.add(OpenWithPassword);
	fileMenu.add(Save);
	fileMenu.add(SaveAs);
	fileMenu.add(SetPassword);
	fileMenu.add(Quit);
	fileMenu.addSeparator();
		
	for(int i = 0; i < 4; i++)
	    fileMenu.getItem(i).setIcon(null);

	editMenu.add(Cut);
	editMenu.add(Copy);
	editMenu.add(Paste);

	editMenu.getItem(0).setText("Cut out");
	editMenu.getItem(1).setText("Copy");
	editMenu.getItem(2).setText("Paste");
		
	JToolBar tool = new JToolBar();
	add(tool,BorderLayout.NORTH);
	tool.add(New);
	tool.add(Open);
	tool.add(OpenWithPassword);
	tool.add(Save);
	tool.add(SetPassword);
	tool.addSeparator();
		
	JButton cut = tool.add(Cut), 
	    cop = tool.add(Copy), 
	    pas = tool.add(Paste);
		
	cut.setText(null); 
	cut.setText("Cut");
	cop.setText(null); 
	cop.setLabel("Copy");
	pas.setText(null); 
	pas.setText("Paste");

	add(status, BorderLayout.SOUTH);
	
	Save.setEnabled(false);
	SaveAs.setEnabled(false);
	SetPassword.setEnabled(false);
		
	setDefaultCloseOperation(EXIT_ON_CLOSE);
	pack();
	area.addKeyListener(k1);
	setTitle(currentFile.name);

	setLocationRelativeTo(null);
	
	addWindowListener(new WindowAdapter() {
		public void windowOpened( WindowEvent e ){
		    area.requestFocus();
		}
	    }); 	
	setVisible(true);
	
	if(fileToOpen != null) {
		File selectedFile = new File(fileToOpen);
		String password = askPassword("Password", "If " + selectedFile.getName() + " is crypted, enter the password here:");
		if(password != null && password.length() > 0)
			readInFile(selectedFile.getAbsolutePath(), password);
		else
			readInFile(selectedFile.getAbsolutePath());
		SaveAs.setEnabled(true);
		SetPassword.setEnabled(true);
	}
    }
	
    private KeyListener k1 = new KeyAdapter() {
	    public void keyPressed(KeyEvent e) {
		currentFile.changed = true;
		Save.setEnabled(true);
		SaveAs.setEnabled(true);
		SetPassword.setEnabled(true);
	    }
	};
    
    Action New = new AbstractAction("New") {
	    public void actionPerformed(ActionEvent e) {
		saveCurrentIfChanged();
		area.setText(null);
		setTitle(newFileTitle);
		SaveAs.setEnabled(true);
		SetPassword.setEnabled(true);
	    }
	};
    
    Action Open =
	new AbstractAction("Open") {
	    public void actionPerformed(ActionEvent e) {
		saveCurrentIfChanged();
		if(dialog.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
		    readInFile(dialog.getSelectedFile().getAbsolutePath());
		}
		SaveAs.setEnabled(true);
		SetPassword.setEnabled(true);
	    }
	};
    
    Action OpenWithPassword =
	new AbstractAction("Open with password...") {
	    public void actionPerformed(ActionEvent e) {
		saveCurrentIfChanged();
		if(dialog.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
			File selectedFile = dialog.getSelectedFile();

			String password = askPassword("Password", "Enter the password for " 
				+ selectedFile.getName());
			if(password != null && password.length() > 0)
				readInFile(selectedFile.getAbsolutePath(), password);
			else {
				status.setText("Action cancelled");
			}
		}
		SaveAs.setEnabled(true);
		SetPassword.setEnabled(true);
	    }
	};

    Action Save = new AbstractAction("Save") {
	    public void actionPerformed(ActionEvent e) {
		if(currentFile.name.equals(newFileTitle))
		    saveFileAs();
		else
		    saveFile();
	    }
	};
	
    Action SaveAs = new AbstractAction("Save as...") {
	    public void actionPerformed(ActionEvent e) {
		saveFileAs();
	    }
	};
	
    Action SetPassword = new AbstractAction("Password...") {
	    public void actionPerformed(ActionEvent e) {
		if(currentFile.password == null) {
		    setPassword();
		}
		else {
		    changePassword();
		}
	    }
	};
	
    Action Quit = new AbstractAction("Quit") {
	    public void actionPerformed(ActionEvent e) {
		quit();
	    }
	};
	
    ActionMap m = area.getActionMap();
    Action Cut = m.get(DefaultEditorKit.cutAction);
    Action Copy = m.get(DefaultEditorKit.copyAction);
    Action Paste = m.get(DefaultEditorKit.pasteAction);

    static String askPassword(String title, String caption) {
	JPanel panel = new JPanel();
	JLabel label = new JLabel(caption);
	JPasswordField pass = new JPasswordField(15);
	panel.add(label);
	panel.add(pass);
	String[] options = new String[]{"OK", "Cancel"};
	int option = JOptionPane.showOptionDialog(null, panel, title,
		JOptionPane.NO_OPTION, JOptionPane.PLAIN_MESSAGE,
		null, options, options[0]);
		
	if(option == 0) // pressing OK button
		return new String(pass.getPassword());
	else
		return null;
    }
    
    private void quit() {
	saveCurrentIfChanged();
	System.exit(0);
    }
    
    private void saveFileAs() {
	if(dialog.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
	    currentFile.name = dialog.getSelectedFile().getAbsolutePath();
	    saveFile();
	}
    }
	
    private void saveCurrentIfChanged() {
	if(currentFile.changed) {
	    if(JOptionPane.showConfirmDialog(this, 
					     "Would you like to save "
					     + currentFile.name +" ?",
					     "Save",
					     JOptionPane.YES_NO_OPTION)
	       == JOptionPane.YES_OPTION)
		saveFile();
	}
    }

    private void setPassword() {
	boolean hasCurrentPassword = (currentFile.password != null);
	String s1 = askPassword("Password", 
		"Enter a" + (hasCurrentPassword ? " new " : "") + " password");
	if(s1 == null || s1.length() == 0) {
	    JOptionPane.showMessageDialog
		(this,
		 "The password has not been " + (hasCurrentPassword ? "changed" : "set"));
	}
	else {
		String s2 = askPassword("Password", "Enter the" + (hasCurrentPassword ? " new " : "")
		 + " password a second time");
	    if(s2 == null || s2.length() == 0 || s2.equals(s1) == false) {
		JOptionPane.showMessageDialog
		    (this,
		     "You did not enter the same password. "
			+"The password has not been " + (hasCurrentPassword ? "changed" : "set"));
	    }
	    else {
		currentFile.password = s2;
		if(hasCurrentPassword) {
		    JOptionPane.showMessageDialog
			(this, "The password has been changed.");
		}
		else {
		    JOptionPane.showMessageDialog
			(this,
			 "The password has been set and it will be used to encrypt the file when you save it.");
		}
	    }
	}	
    }
    
    private void changePassword() {
	String old = askPassword("Password", "First enter the current password");
	if(old == null || old.length() == 0) {
	    JOptionPane.showMessageDialog
		(this,
		 "The password has not been changed");
	}
	else if(old.equals(currentFile.password) == false) {
	    JOptionPane.showMessageDialog
		(this,
		 "Sorry, you entered the wrong password");
	}
	else {
	    setPassword();
	}
    }
	
    private void readInFile(String fileName) {
	readInFile(fileName, null);
    }
    
    private void readInFile(String fileName, String password) {
	try {
	    if(password == null) {
		Reader r = new InputStreamReader
		    (new FileInputStream(fileName),
		     Charset.forName("UTF-8"));
		
		area.read(r, null);
		r.close();
		status.setText("Opened file " + fileName);
	    }
	    else {
		try {
		    InputStream in = new BufferedInputStream
			(new FileInputStream(fileName));
		    InputStream in_decrypted = PBE.decrypt(in,
							   password.toCharArray());
		    if(in_decrypted == null) {
			in.close();
			in_decrypted.close();
			JOptionPane.showMessageDialog
			    (this,
			     "Error while reading " + fileName);
			status.setText("Error while reading " + fileName);
		    }
		    else {
			Reader r = new BufferedReader
				(new InputStreamReader(in_decrypted, Charset.forName("UTF-8")));
			area.read(r, null);
			in.close();
			in_decrypted.close();
			status.setText("Opened password-protected file "
				       + fileName);
		    }
		}
		catch(NoSuchProviderException exc) {
		}
		catch(WrongPasswordException exc) {
		    JOptionPane.showMessageDialog(this,
						  "This password could not decrypt the file");
		}
	    }
	    currentFile = FileInfo.newFromFile(fileName, password);
	    setTitle(currentFile.name);
	    currentFile.changed = false;
	}
	catch(IOException e) {
	    Toolkit.getDefaultToolkit().beep();
	    JOptionPane.showMessageDialog(this,
					  "Editor can't find the file called "
					  + fileName + " : " + e.toString());
	}
    }
	
    private void saveFile() {
	try {
	    if(currentFile.password == null) {
		Writer w = new OutputStreamWriter
		    (new FileOutputStream(currentFile.name),
		     Charset.forName("UTF-8"));
						 
		area.write(w);
		w.close();
		status.setText("The file has been saved as "
			       + currentFile.name + " (" + new Date() + ")");
	    }
	    else {
		OutputStream out =
		    new BufferedOutputStream(new FileOutputStream
					     (currentFile.name));
		String text = area.getText();
		PBE.encryptString(text,
				  out,
				  currentFile.password.toCharArray(),
				  currentFile.name,
				  new Date(), /* now */
				  false,
				  false);
		status.setText("The file has been encrypted and saved as "
			       + currentFile.name);
	    }
	    currentFile.changed = false;
	    Save.setEnabled(false);
	}
	catch(IOException e) {
	}
	catch(NoSuchProviderException exc) {
	}
    }
	
    public  static void main(String[] arg) {
	new CryptEdit(arg);
    }
}
