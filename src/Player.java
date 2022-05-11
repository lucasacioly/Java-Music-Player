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

import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ThreadLocalRandom;

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
    private boolean scrubber_pressed = false;
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
    private int scrubbed_frame;
    private List<String> randomlist;
    private int rand_index = 0;


    private int current_id;

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
        ActionListener buttonListenerPlayNow = e -> start_song(window.getSelectedSong());
        ActionListener buttonListenerShuffle = e -> shuffle_enabler();
        ActionListener buttonListenerPrevious = e -> previous();
        ActionListener buttonListenerPlayPause = e -> Play_Pause();
        ActionListener buttonListenerStop = e -> stop();
        ActionListener buttonListenerNext = e -> next();
        ActionListener buttonListenerRepeat = e -> repeat_enabler();

        MouseListener scrubberListenerClick = new MouseListener(){
            @Override
            public void mouseClicked(MouseEvent e) {}

            @Override
            public void mouseReleased(MouseEvent e){release();}

            @Override
            public void mousePressed(MouseEvent e){press();}

            @Override
            public void mouseEntered(MouseEvent e){}

            @Override
            public void mouseExited(MouseEvent e){}
        };

        MouseMotionListener scrubberListenerMotion = new MouseMotionListener(){
            @Override
            public void mouseDragged(MouseEvent e){drag();}

            @Override
            public void mouseMoved(MouseEvent e){}

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
                    String[] randomArray = new String[newQueue.length];
                    for (int i = 0; i < playerQueue.length; i++){
                        newQueue[i] = playerQueue[i];
                        randomArray[i] = playerQueue[i][5];
                    }
                    newQueue[playerQueue.length] = info;
                    randomArray[playerQueue.length] = info[5];
                    randomlist = Arrays.asList(randomArray);

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

            // remover da fila de músicas
            int idx = 0;
            for (int i = 0; i < playerQueue.length - 1; i++) {
                if (Objects.equals(filePath, playerQueue[idx][5])) {
                    idx = idx + 1;
                }
                new_queue[i] = playerQueue[idx];
                idx++;
            }
            // se o objeto removido estava tocando, interrompe a reprodução e reseta o miniplayer
            if (currentSong != null && Objects.equals(filePath, currentSong.getFilePath())){
                try{exit_song = true;
                    Thread.sleep(300);
                    window.resetMiniPlayer();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

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
    public void start_song(String filePath) {
        Thread start_song = new Thread(() -> {
            try {
                this.lock.lock();
                // verificar se há uma música tocando para substitui-la pela nova
                if (Song_Playing != null) {
                    exit_song = true;
                    Thread.sleep(50);
                    playerPaused = false;
                    Thread.sleep(300);
                }

                // pegar as informações na queue

                String[] playing = new String[7];
                for (int i = 0; i < playerQueue.length; i++) {
                    if (Objects.equals(filePath, playerQueue[i][5])) {
                        playing = playerQueue[i];
                        current_id = i;
                        break;
                    }
                }
                // pegar as informações na Song_list
                currentSong = fetch_in_song_array(filePath);
                totFrames = currentSong.getNumFrames();
                //System.out.println(totFrames);
                newFrame = 0;
                tot_time = totFrames*Math.round(currentSong.getMsPerFrame()); //////////////////////////////
                current_time = 0;                                    /////////////////////////////
                window.setTime(current_time, tot_time);

                window.updatePlayingSongInfo(playing[0], playing[1], playing[2]);

                // preparar para tocar
                File file = new File(filePath);
                device = FactoryRegistry.systemRegistry().createAudioDevice();
                device.open(decoder = new Decoder());
                bitstream = new Bitstream(new BufferedInputStream(new FileInputStream(file)));

                song_is_playing = true;
                playerPaused = false;
                exit_song = false;

                window.setEnabledScrubber(song_is_playing);
                window.setEnabledPlayPauseButton(song_is_playing);
                window.updatePlayPauseButtonIcon(playerPaused);
                window.setEnabledStopButton(song_is_playing);
                window.setEnabledNextButton(song_is_playing);
                window.setEnabledPreviousButton(song_is_playing);
                // TOCAR A MUSICA

                Song_Playing = new Thread(new Playtask());
                Song_Playing.start();

            } catch (IOException | JavaLayerException | InterruptedException e) {
                e.printStackTrace();
            } finally {
                this.lock.unlock();
            }
        });
        start_song.start();
    }

    public void stop() {
        Thread stop = new Thread(() -> {
            try{
                if (Song_Playing != null){
                    Thread.sleep(200);
                    exit_song = true;
                    Thread.sleep(200);
                    window.resetMiniPlayer();
                }
            }catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        stop.start();
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

    public void next() {
        //lock.lock();
        if (shuffle && rand_index + 1 < randomlist.size()){
            start_song(randomlist.get(rand_index + 1));
            rand_index++;
        }
        else if (!shuffle && current_id + 1 < playerQueue.length){
            start_song(playerQueue[current_id + 1][5]);
        }
        //lock.unlock();
    }

    public void previous() {
        //lock.lock();
        if (shuffle && rand_index - 1 > -1){
            start_song(randomlist.get(rand_index - 1));
            rand_index--;
        }
        else if (!shuffle && current_id - 1 > -1){
            start_song(playerQueue[current_id - 1][5]);
        }
        //lock.unlock();
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

    // --------------------- Solving progress sliding-------------------- //

    private void press(){

        Thread press = new Thread(()->{
            try {
                this.lock.lock();
                scrubber_pressed = true;

                if (song_is_playing && currentSong != null) {
                    current_time = window.getScrubberValue();
                    scrubbed_frame = current_time / Math.round(currentSong.getMsPerFrame());
                }

                this.lock.unlock();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        press.start();
    }

    private void drag(){
        Thread drag = new Thread(() -> {
            this.lock.lock();
            if (song_is_playing && currentSong != null){
                current_time = window.getScrubberValue();
                scrubbed_frame = current_time/Math.round(currentSong.getMsPerFrame());
                window.setTime(current_time, tot_time);
                //System.out.println(frame); // is returning song's frame in current scrubber position
            }
            this.lock.unlock();
        });
        drag.start();
    }

    private void release(){

        Thread release = new Thread(() -> {

            try {
                this.lock.lock();
                current_time = window.getScrubberValue();
                currentFrame = 0;
                //System.out.println(current_time);
                scrubbed_frame = current_time/Math.round(currentSong.getMsPerFrame());
                newFrame = scrubbed_frame;

                boolean paused = playerPaused;

                // verificar se há uma música tocando para substitui-la pela nova
                if (Song_Playing != null) {
                    exit_song = true;
                    Thread.sleep(50);
                    playerPaused = false;
                    Thread.sleep(300);
                }

                playerPaused = paused;

                // pegar as informações na Song_list
                totFrames = currentSong.getNumFrames();
                //System.out.println(totFrames);

                tot_time = totFrames*Math.round(currentSong.getMsPerFrame()); //////////////////////////////

                window.setTime(current_time, tot_time);


                // preparar para tocar
                File file = new File(currentSong.getFilePath());
                device = FactoryRegistry.systemRegistry().createAudioDevice();
                device.open(decoder = new Decoder());
                bitstream = new Bitstream(new BufferedInputStream(new FileInputStream(file)));

                song_is_playing = true;
                exit_song = false;

                window.setEnabledScrubber(song_is_playing);
                window.setEnabledPlayPauseButton(song_is_playing);
                window.updatePlayPauseButtonIcon(playerPaused);
                window.setEnabledStopButton(song_is_playing);
                window.setEnabledNextButton(song_is_playing);
                window.setEnabledPreviousButton(song_is_playing);
                // TOCAR A MUSICA

                Thread.sleep(100);

                Song_Playing = new Thread(new Playtask());
                Song_Playing.start();

                scrubber_pressed = false;

                this.lock.unlock();

            } catch (InterruptedException | FileNotFoundException | JavaLayerException e) {
                e.printStackTrace();
            }
        });
        release.start();
    }

    // ----------------------------------------------------------------  //


    // ------------------ Solving Repeat music -----------------------  //
    private void repeat_enabler(){
        repeat = !repeat;
    }

    // ---------------------------------------------------------------  //


    // ----------------- Solving random selection --------------------  //
    private void shuffle_enabler(){
        shuffle = !shuffle;
        rand_index = 0;
        System.out.println(shuffle);
        if (shuffle){
            Collections.shuffle(randomlist);
        }
    }
    // ---------------------------------------------------------------  //

    // Play task
    class Playtask extends Thread {
        @Override
        public void run(){

            try{
                System.out.println(current_time);
                System.out.println("to frame: " + scrubbed_frame);
                skipToFrame(scrubbed_frame);
                System.out.println("current frame: " + currentFrame);
                System.out.println("newFrame: " + newFrame);
                System.out.println("player is paused: " + playerPaused);
                while (song_is_playing) {

                    while (playerPaused){
                        Thread.sleep(10);
                        if(exit_song){
                            break;
                        }
                    }

                    if (exit_song){
                        Thread.sleep(200);
                        break;
                    }

                    song_is_playing = playNextFrame();
                    window.setTime(current_time, tot_time); ///////////////////////////////

                    if (newFrame <= totFrames){
                        newFrame++;                         /////////////////////////////
                    }

                    if (!scrubber_pressed) {
                        current_time = newFrame * Math.round(currentSong.getMsPerFrame());
                    }

                }
                if (!exit_song){
                    if (repeat){
                        start_song(currentSong.getFilePath());
                    }
                    else{
                        if (shuffle){
                            //int random_index = ThreadLocalRandom.current().nextInt(0, playerQueue.length);
                            //System.out.println("random: " + random_index);
                            //start_song(playerQueue[random_index][5]);
                            if (rand_index == 0){
                                start_song(randomlist.get(rand_index));
                            }
                            else {
                                next();
                            }
                        }
                        else {
                            if (Objects.equals(currentSong.getFilePath(), playerQueue[playerQueue.length-1][5])) {
                                window.resetMiniPlayer();
                            }
                            else{
                                next();
                            }
                        }
                    }

                }

            } catch (JavaLayerException | InterruptedException e) {
                e.printStackTrace();
            }
            this.interrupt();
        }

    }


}
