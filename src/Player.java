import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Player {

    /**
     * The MPEG audio bitstream.
     */
    private Bitstream bitstream;
    /**
     * The MPEG audio decoder.
     */
    private Decoder decoder;
    /**
     * The AudioDevice the audio samples are written to.
     */
    private AudioDevice device;

    private PlayerWindow window;

    private boolean repeat = false;
    private boolean shuffle = false;
    private boolean playerEnabled = false;
    private boolean playerPaused = true;
    private boolean song_is_playing = false;
    private boolean exit_song = false;
    private Song currentSong;
    private int currentFrame = 0;
    private int newFrame;
    private static int totFrames;
    String[][] playerQueue = new String[0][7];
    Thread Song_Playing;
    private final Lock lock = new ReentrantLock();
    private int current_time;
    private int tot_time;
    Song[] added_songs = new Song[0];

    public Player() {


        String windowTitle = "MP3-PLAYER"; // título da janela

        ActionListener buttonListenerRemove = e -> removeFromQueue(window.getSelectedSong());
        ActionListener buttonListenerAddSong =  e -> {
            try {
                addToQueue();
            } catch (InvalidDataException | BitstreamException | IOException | UnsupportedTagException ex) {
                ex.printStackTrace();
            }
        };
        ActionListener buttonListenerPlayNow = e -> start(window.getSelectedSong());
        ActionListener buttonListenerShuffle = e -> empty();
        ActionListener buttonListenerPrevious = e -> previous();
        ActionListener buttonListenerPlayPause = e -> Play_Pause();
        ActionListener buttonListenerStop = e -> stop();
        ActionListener buttonListenerNext = e -> next();
        ActionListener buttonListenerRepeat = e -> empty();

        MouseListener scrubberListenerClick = new MouseListener(){
            @Override
            public void mouseClicked(MouseEvent e) {
            }

            @Override
            public void mouseReleased(MouseEvent e){
            }

            @Override
            public void mousePressed(MouseEvent e){
            }

            @Override
            public void mouseEntered(MouseEvent e){}

            @Override
            public void mouseExited(MouseEvent e){}
        };

        MouseMotionListener scrubberListenerMotion = new MouseMotionListener(){
            @Override
            public void mouseDragged(MouseEvent e) {
            }

            @Override
            public void mouseMoved(MouseEvent e){

            }

        };

        try {
            EventQueue.invokeAndWait(() -> window = new PlayerWindow(
                    windowTitle,
                    getQueueAsArray(),
                    buttonListenerPlayNow,
                    buttonListenerRemove,
                    buttonListenerAddSong,
                    buttonListenerShuffle,
                    buttonListenerPrevious,
                    buttonListenerPlayPause,
                    buttonListenerStop,
                    buttonListenerNext,
                    buttonListenerRepeat,
                    scrubberListenerClick,
                    scrubberListenerMotion));
        } catch (InterruptedException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }


    //<editor-fold desc="Essential">

    /**
     * @return False if there are no more frames to play.
     */
    private boolean playNextFrame() throws JavaLayerException {
        // TODO Is this thread safe?
        if (device != null) {
            Header h = bitstream.readFrame();
            if (h == null) return false;

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            device.write(output.getBuffer(), 0, output.getBufferLength());
            bitstream.closeFrame();
        }

        return true;
    }

    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        // TODO Is this thread safe?
        Header h = bitstream.readFrame();
        if (h == null) return false;
        bitstream.closeFrame();
        currentFrame++;
        return true;
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     * @throws BitstreamException
     */
    private void skipToFrame(int newFrame) throws BitstreamException {
        // TODO Is this thread safe?
        if (newFrame > currentFrame) {
            int framesToSkip = newFrame - currentFrame;
            boolean condition = true;
            while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
        }
    }
    //</editor-fold>

    //<editor-fold desc="Queue Utilities">
    public void addToQueue() throws InvalidDataException, UnsupportedTagException, IOException, BitstreamException {
        Thread add_song = new Thread(() ->{

            Song new_song = null;
            try {
                new_song = window.getNewSong();
                String[] info = new_song.getDisplayInfo();
                lock.lock();
                // vreifica se new_song já foi adicionado anteriormente
                boolean is_in_queue = false;
                for (int i = 0; i < playerQueue.length; i++){
                    if (Objects.equals(playerQueue[i][5], info[5])) {
                        is_in_queue = true;
                        break;
                    }
                }

                if (!is_in_queue){ // se não estava na queue então dicionamos

                    // adição ao array de songs
                    boolean is_in_added_songs = false;
                    //System.out.println(added_songs.length);
                    for (int i = 0; i < added_songs.length; i++){
                        if (Objects.equals(new_song.getFilePath(), added_songs[i].getFilePath())) {
                            is_in_added_songs = true;
                            break;
                        }
                        System.out.println(i);
                        System.out.println(is_in_added_songs);
                    }
                    if (!is_in_added_songs){
                        Song[] new_song_array = new Song[added_songs.length+1];
                        for (int i = 0; i < added_songs.length; i++) {
                            new_song_array[i] = added_songs[i];
                        }
                        System.out.println(added_songs.length);
                        new_song_array[added_songs.length] = new_song;
                        added_songs = new_song_array;
                    }
                    for (int i = 0; i < added_songs.length; i++){
                        System.out.println(added_songs[i]);
                    }
                    // adição à queue
                    String[][] newQueue = new String[playerQueue.length+1][7];
                    for (int i = 0; i < playerQueue.length; i++){
                        newQueue[i] = playerQueue[i];
                    }
                    newQueue[playerQueue.length] = info;

                    playerQueue = newQueue;
                    window.updateQueueList(newQueue);
                }
                else {
                    System.out.println("song is already in the list");
                }

                lock.unlock();
            } catch (IOException | BitstreamException | UnsupportedTagException | InvalidDataException e) {
                e.printStackTrace();
            }

        });
        add_song.start();

    }

    public void removeFromQueue(String filePath) {
        Thread remove = new Thread(() -> {
            lock.lock();
            String[][] new_queue = new String[playerQueue.length - 1][7];

            int idx = 0;
            for (int i = 0; i < playerQueue.length - 1; i++) {
                if (Objects.equals(filePath, playerQueue[idx][5])) {
                    idx = idx + 1;
                }
                new_queue[i] = playerQueue[idx];
                idx++;
            }
            if (Objects.equals(filePath, currentSong.getFilePath())){
                exit_song = true;
                window.resetMiniPlayer();
                window.setTime(0,0);
            }
            playerQueue = new_queue;
            window.updateQueueList(new_queue);
            lock.unlock();
        });
        remove.start();
    }

    public String[][] getQueueAsArray() {
        return null;
    }

    //</editor-fold>

    //<editor-fold desc="Controls">
    public void start(String filePath) {
        Thread start = new Thread(() -> {
            try {
                this.lock.lock();
                // verificar se há uma música tocando para substitui-la pela nova
                if (Song_Playing != null){
                    exit_song = true;
                }
                // pegar as informações na queue
                int idx = 0;
                String[] playing = new String[7];
                for (int i = 0; i < playerQueue.length; i++) {
                    if (Objects.equals(filePath, playerQueue[idx][5])) {
                        playing = playerQueue[idx];
                    }
                    idx++;
                }
                // pegar as informações na Song_list
                currentSong = fetch_in_song_array(filePath);
                totFrames = currentSong.getNumFrames();
                newFrame = 0;
                tot_time = totFrames*Math.round(currentSong.getMsPerFrame()); //////////////////////////////
                current_time = 0;                                    /////////////////////////////
                window.setTime(current_time, tot_time);
                /*
                removeFromQueue(filePath);

                String[][] new_queue = new String[playerQueue.length + 1][7];
                new_queue[0] = playing;
                int new_queue_idx = 1;
                int current_queue_idx = 0;

                for (int i = 0; i < playerQueue.length; i++) {
                    new_queue[new_queue_idx] = playerQueue[current_queue_idx];
                    new_queue_idx++;
                    current_queue_idx++;
                }
                playerQueue = new_queue;
                window.updateQueueList(new_queue);
                */
                window.updatePlayingSongInfo(playing[0], playing[1], playing[2]);

                // preparar para tocar
                File file = new File(filePath);
                int maxFrames = new Mp3File(file).getFrameCount();
                device = FactoryRegistry.systemRegistry().createAudioDevice();
                device.open(decoder = new Decoder());
                bitstream = new Bitstream(new BufferedInputStream(new FileInputStream(file)));

                song_is_playing = true;
                playerPaused = false;
                exit_song = false;
                window.setEnabledScrubber(song_is_playing);
                window.setEnabledPlayPauseButton(song_is_playing);
                window.updatePlayPauseButtonIcon(playerPaused);
                // TOCAR A MUSICA

                Song_Playing = new Thread(new Playtask());
                Song_Playing.start();

            } catch (IOException | InvalidDataException | UnsupportedTagException | JavaLayerException e) {
                e.printStackTrace();
            }finally {
                this.lock.unlock();
            }
        });
        start.start();
    }

    public void stop() {
    }

    public void Play_Pause() {
        if (song_is_playing && !playerPaused){
            playerPaused = true;
        }
        else if (song_is_playing && playerPaused){
            playerPaused = false;

        }
        window.updatePlayPauseButtonIcon(playerPaused);
    }

    public void resume() {
    }

    public void next() {
    }

    public void previous() {
    }

    public void empty() {
    }
    //</editor-fold>

    //<editor-fold desc="Getters and Setters">

    //</editor-fold>

    public Song fetch_in_song_array(String filePath){
        for (int i = 0; i < added_songs.length; i++){
            if (Objects.equals(added_songs[i].getFilePath(), filePath)){
                return added_songs[i];
            }
        }
        return null;
    }

    // Play task
    class Playtask extends Thread {
        @Override
        public void run(){

            try{
                while (song_is_playing) {
                    if (exit_song){
                        break;
                    }
                    song_is_playing = playNextFrame();
                    window.setTime(current_time, tot_time); ///////////////////////////////
                    if (newFrame < totFrames){
                        newFrame++;                         //////////////////////////////
                    }
                    current_time = newFrame*Math.round(currentSong.getMsPerFrame());

                    while (playerPaused){
                        try {
                            sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (JavaLayerException e) {
                e.printStackTrace();
            }

        }

    }


}
