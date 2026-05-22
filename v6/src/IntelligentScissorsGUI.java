import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.filechooser.FileNameExtensionFilter;

public class IntelligentScissorsGUI extends JFrame {
    private JPanel mainPanel;
    private JPanel controlPanelTop;
    private JPanel controlPanelBottom;
    private JButton importButton;
    private JButton clearButton;
    private JButton cutoutButton;
    private JButton saveButton;
    private JLabel statusLabel;
    private ImagePanel imagePanel;
    private Preproc preprocessor;
    private PathFinder pathFinder;

    public IntelligentScissorsGUI() {
        setTitle("Intelligent Scissors Tool");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 850);
        setLocationRelativeTo(null);

        mainPanel = new JPanel(new BorderLayout());

        controlPanelTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        importButton = new JButton("选择图像");
        clearButton = new JButton("清除");
        statusLabel = new JLabel("选择图像以开始.");

        controlPanelTop.add(importButton);
        controlPanelTop.add(clearButton);
        controlPanelTop.add(statusLabel);

        controlPanelBottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        cutoutButton = new JButton("执行剪切");
        saveButton = new JButton("保存");

        controlPanelBottom.add(cutoutButton);
        controlPanelBottom.add(saveButton);

        imagePanel = new ImagePanel();

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(controlPanelTop, BorderLayout.NORTH);
        northPanel.add(controlPanelBottom, BorderLayout.SOUTH);

        mainPanel.add(northPanel, BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(imagePanel), BorderLayout.CENTER);

        add(mainPanel);

        importButton.addActionListener(e -> importImage());
        clearButton.addActionListener(e -> clearSelection());
        cutoutButton.addActionListener(e -> performCutoutAction());
        saveButton.addActionListener(e -> saveResultAction());

        updateButtonStates(false);
    }

    private void importImage() {
        JFileChooser fileChooser = new JFileChooser();

        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Image Files (*.jpg, *.jpeg, *.png, *.gif)", "jpg", "jpeg", "png", "gif");
        fileChooser.setFileFilter(filter);
        fileChooser.setAcceptAllFileFilterUsed(false);

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                BufferedImage image = ImageIO.read(selectedFile);
                if (image != null) {
                    BufferedImage argbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = argbImage.createGraphics();
                    g.drawImage(image, 0, 0, null);
                    g.dispose();

                    imagePanel.setImage(argbImage);

                    preprocessor = new Preproc(argbImage);
                    pathFinder = new PathFinder(); // PathFinder is mostly stateless

                    imagePanel.setPreprocessor(preprocessor);
                    imagePanel.setPathFinder(pathFinder);

                    updateButtonStates(true);
                    statusLabel.setText("Image loaded: " + selectedFile.getName() + ". Ready to draw path.                                             Left-click: Set points. Middle-click: Finalize/New contour. Right-click: Undo.");
                } else {
                    JOptionPane.showMessageDialog(this, "Could not load image file.", "Load Error", JOptionPane.ERROR_MESSAGE);
                    updateButtonStates(false);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error loading image: " + ex.getMessage(), "Load Error", JOptionPane.ERROR_MESSAGE);
                updateButtonStates(false);
                ex.printStackTrace();
            }
        }
    }

    private void clearSelection() {
        boolean imageStillLoaded = false;
        if (imagePanel != null) {
            imagePanel.clearPath();
            if (imagePanel.getImage() != null) {
                imageStillLoaded = true;
            }
        }

        if (imageStillLoaded) {
            statusLabel.setText("Selection cleared. Ready to draw path on current image.");
        } else {
            statusLabel.setText("Please import an image to start.");
        }
        updateButtonStates(imageStillLoaded);
    }

    private void performCutoutAction() {
        if (imagePanel != null && imagePanel.getImage() != null && imagePanel.hasPath()) {
            imagePanel.performCutout();

        } else if (imagePanel != null && imagePanel.getImage() == null) {
            JOptionPane.showMessageDialog(this, "No image loaded to perform cutout on.", "Cutout Error", JOptionPane.ERROR_MESSAGE);
        }
        else {
            JOptionPane.showMessageDialog(this, "No path drawn or path not closed.", "Cutout Error", JOptionPane.INFORMATION_MESSAGE);
        }
        updateButtonStates(imagePanel != null && imagePanel.getImage() != null);
    }

    private void saveResultAction() {
        if (imagePanel == null || imagePanel.getImage() == null) {
            JOptionPane.showMessageDialog(this, "No image to save.", "Save Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        BufferedImage imageToSave = imagePanel.getResultImage();

        if (imageToSave == null) {
            JOptionPane.showMessageDialog(this, "No result to save (path/cutout might be empty or invalid).", "Save Error", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Result As");
        fileChooser.setSelectedFile(new File("intelligent_scissors_output.png")); // Default name

        FileNameExtensionFilter pngFilter = new FileNameExtensionFilter("PNG Image (*.png)", "png");
        fileChooser.setFileFilter(pngFilter);
        fileChooser.setAcceptAllFileFilterUsed(false); // Only allow PNG saving

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String filePath = file.getAbsolutePath();
            if (!filePath.toLowerCase().endsWith(".png")) {
                file = new File(filePath + ".png");
            }

            try {
                ImageIO.write(imageToSave, "png", file);
                statusLabel.setText("Result saved to: " + file.getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving image: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    private void updateButtonStates(boolean imageIsCurrentlyLoaded) {

        clearButton.setEnabled(imageIsCurrentlyLoaded);
        cutoutButton.setEnabled(imageIsCurrentlyLoaded);
        saveButton.setEnabled(imageIsCurrentlyLoaded);

        if (!imageIsCurrentlyLoaded) {
            statusLabel.setText("Please import an image to start.");
        } else if (imagePanel != null && imagePanel.hasPath()){

        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            IntelligentScissorsGUI gui = new IntelligentScissorsGUI();
            gui.setVisible(true);
        });
    }
}