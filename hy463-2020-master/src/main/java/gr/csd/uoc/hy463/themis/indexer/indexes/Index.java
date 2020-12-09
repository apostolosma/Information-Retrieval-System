/*
 * themis - A fair search engine for scientific articles
 *
 * Currently over the Semantic Scholar Open Research Corpus
 * http://s2-public-api-prod.us-west-2.elasticbeanstalk.com/corpus/
 *
 * Collaborative work with the undergraduate/graduate students of
 * Information Retrieval Systems (hy463) course
 * Spring Semester 2020
 *
 * -- Writing code during COVID-19 pandemic times :-( --
 *
 * Aiming to participate in TREC 2020 Fair Ranking Track
 * https://fair-trec.github.io/
 *
 * Computer Science Department http://www.csd.uoc.gr
 * University of Crete
 * Greece
 *
 * LICENCE: TO BE ADDED
 *
 * Copyright 2020
 *
 */
package gr.csd.uoc.hy463.themis.indexer.indexes;

import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.utils.DocumentStructure;
import gr.csd.uoc.hy463.themis.utils.Occurrence;
import gr.csd.uoc.hy463.themis.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.text.AbstractDocument;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * This class holds all information related to a specific (partial or not) index
 * in memory. It also knows how to store this information to files
 *
 * @author Panagiotis Papadakos (papadako@ics.forth.gr)
 */
public class Index {

    // Partial indexes have an id > 0 and corresponding idx files are stored in
    // INDEX_PATH/id while for a full index, idx files are stored in INDEX_PATH
    // e.g., the first partial index files are saved to INDEX_PATH/1/
    private int id = 0; // the id of the index that is used for partial indexes
    private static long lastDocBatch = 0;

    private static final Logger __LOGGER__ = LogManager.getLogger(Index.class);
    private Config __CONFIG__;  // configuration options

    // The path of index
    private String __INDEX_PATH__ = null;
    // Filenames of indexes
    private String __VOCABULARY_FILENAME__ = null;
    private String __POSTINGS_FILENAME__ = null;
    private String __DOCUMENTS_FILENAME__ = null;
    private String __CONTENTS_FILENAME__ = null;
    private static HashMap<Integer, Long> offsets = new HashMap<>();
    //  private static HashMap<Integer, String> keyEncoding = new HashMap<>();
    private static HashMap<Integer,Pair<String,Long>> keyEnc_Offsets=new HashMap<>(131072);
    // We also need to store any information about the vocabulary,
    // posting and document file in memory
    // For example a TreeMap holds entries sorted which helps with storing the
    // vocabulary file
    private TreeMap<String, Integer> __VOCABULARY__ = null;
    //This hashmap contains each doc's weight offsets , so it is faster to write in disk
    //private static HashMap<String, Pair<Long,Long>> wpOffsets = new HashMap<String,Pair<Long,Long>>();

    // We have to hold also other appropriate data structures for postings / documents
    public Index(Config config) {
        __CONFIG__ = config;
        init();
    }

    /**
     * Initialize things
     */
    private void init() {
        __VOCABULARY_FILENAME__ = __CONFIG__.getVocabularyFileName();
        __POSTINGS_FILENAME__ = __CONFIG__.getPostingsFileName();
        __DOCUMENTS_FILENAME__ = __CONFIG__.getDocumentsFileName();
        __INDEX_PATH__ = __CONFIG__.getIndexPath();
        __CONTENTS_FILENAME__ = __CONFIG__.getContentsFileName();
    }

    /**
     * This method is responsible for dumping all information held by this index
     * to the filesystem in the directory INDEX_PATH/id. If id = 0 then it dumps
     * every idx files to the INDEX_PATH
     *
     * Specifically, it creates:
     *
     * =========================================================================
     * 1) VOCABULARY FILE => vocabulary.idx (Normal Sequential file)
     *
     * This is a normal sequential file where we write in lexicographic order
     * the following entries separated by space: | TERM (a term of the
     * vocabulary) | DF document frequency of this term | POINTER_TO_POSTING
     * (the offset in the posting.idx, this is a long number) |
     *
     * =========================================================================
     * 2) POSTING FILE => posting.idx (Random Access File)
     *
     * For each entry it stores: | DOCUMENT_ID (40 ASCII chars => 40 bytes) | TF
     * (int => 4 bytes) | POINTER_TO_DOCUMENT_FILE (long => 4 bytes)
     *
     * =========================================================================
     * 3) DOCUMENTS FILE => documents.idx (Random Access File)
     *
     * For each entry it stores: | DOCUMENT_ID (40 ASCII chars => 40 bytes) |
     * Title (variable bytes / UTF-8) | Author_1,Author_2, ...,Author_k
     * (variable bytes / UTF-8) | AuthorID_1, AuthorID_2, ...,Author_ID_k
     * (variable size /ASCII) | Year (short => 2 bytes)| Journal Name (variable
     * bytes / UTF-8) | The weight (norm) of Document (double => 8 bytes)|
     * Length of Document (int => 4 bytes) | PageRank Score (double => 8 bytes
     * => this will be used in the second phase of the project)
     *
     * ==> IMPORTANT NOTES
     *
     * For strings that have a variable size, just add as an int (4 bytes)
     * prefix storing the size in bytes of the string. Also make sure that you
     * use the correct representation ASCII (1 byte) or UTF-8 (2 bytes). For
     * example the doc id is a hexadecimal hash so there is no need for UTF
     * encoding
     *
     * Authors are separated by a comma
     *
     * Author ids are also separated with a comma
     *
     * The weight of the document will be computed after indexing the whole
     * collection by scanning the whole postings list
     *
     * For now add 0.0 for PageRank score (a team will be responsible for
     * computing it in the second phase of the project)
     *
     *
     * @return true/false
     */
    public boolean dump(Indexer indexer) throws IOException {
        long st1,et1;
        long st3,et3,D_timer,P_timer;
        //keyEncoding = indexer.getKeyEncoding();
        keyEnc_Offsets = indexer.getkeyEnc_Offsets();
        String path = indexer.getIndexDirectory();
        RandomAccessFile DocumentFile = new RandomAccessFile(path+"" + __DOCUMENTS_FILENAME__,"rw");
        RandomAccessFile ContentsFile = new RandomAccessFile(path+"" + __CONTENTS_FILENAME__,"rw");
        st1 = System.nanoTime();
        dumpDocFile(indexer,DocumentFile, ContentsFile);
        et1 = System.nanoTime();
        D_timer = et1-st1;
        if(indexer.getMeta().get("Doc dump time: ") != null)
        {
            D_timer += Long.parseLong(indexer.getMeta().get("Doc dump time: "));
            indexer.getMeta().put("Doc dump time: ",Long.toString(D_timer));
        }
        else{
            indexer.getMeta().put("Doc dump time: ",Long.toString(D_timer));
        }
        if (id == 0) {
            // dump to INDEX_PATH
            RandomAccessFile PostingsFile = new RandomAccessFile(path+"" + __POSTINGS_FILENAME__,"rw");
            st3 = System.nanoTime();
            File vocab = new File(path +""+__VOCABULARY_FILENAME__);
            appendPostFile(indexer,PostingsFile,vocab);
            et3 =  System.nanoTime();
            P_timer = et3-st3;
            if(indexer.getMeta().get("Post dump time: ") != null)
            {
                P_timer += Long.parseLong(indexer.getMeta().get("Doc dump time: "));
                indexer.getMeta().put("Post dump time: ",Long.toString(P_timer));
            }
            else{
                indexer.getMeta().put("Doc dump time: ",Long.toString(P_timer));
            }
        } else {
            // dump to INDEX_PATH/id
            String path1 = indexer.getIndexDirectory() + id + "/";
            File f = new File(path1);
            f.mkdir();
            RandomAccessFile PostingsFile = new RandomAccessFile(path1+"" +__POSTINGS_FILENAME__,"rw");

            st3 = System.nanoTime();
            File vocab = new File(path1 +""+__VOCABULARY_FILENAME__);
            appendPostFile(indexer,PostingsFile,vocab);
            et3 =  System.nanoTime();
            P_timer = et3-st3;
            if(indexer.getMeta().get("Post dump time: ") != null)
            {
                P_timer += Long.parseLong(indexer.getMeta().get("Doc dump time: "));
                indexer.getMeta().put("Post dump time: ",Long.toString(P_timer));
            }
            else{
                indexer.getMeta().put("Doc dump time: ",Long.toString(P_timer));
            }
        }
        dumpEncodings(indexer);
        keyEnc_Offsets.clear();
        return false;
    }

    /**
     * Method to write in disk, both vocabulary and postings file at the same time.
     * @param indexer
     * @param PostingsFile
     * @param vocab
     * @throws IOException
     */
    public static void appendPostFile(Indexer indexer,RandomAccessFile PostingsFile,File vocab) throws IOException {
        int size = 1;
        BufferedWriter bw = new BufferedWriter(new FileWriter(vocab));
        TreeMap<String,ArrayList<Occurrence>> v = indexer.getVocab();
        for(String term : v.keySet())
        {
            if (size != v.size()) {
                bw.write(term + " " + v.get(term).size() + " " + PostingsFile.getFilePointer() + "\n");

            }
            else bw.write(term + " " + v.get(term).size() + " " + PostingsFile.getFilePointer());

            ArrayList<Occurrence> oc = v.get(term);
            ByteArrayOutputStream btArr = new ByteArrayOutputStream();

            for(Occurrence o : oc)
            {
                btArr.write(ByteBuffer.allocate(4).putInt(o.getDocId()).array());
                btArr.write(ByteBuffer.allocate(4).putInt(o.getTf()).array());
                btArr.write(longToBytes(offsets.get(o.getDocId())));
            }
            PostingsFile.write(btArr.toByteArray());
            size++;
        }
        PostingsFile.close();
        bw.close();
    }

    /**
     * Methos to write in disk, writes in documents file.
     * @param indexer
     * @param DocumentFile
     * @throws IOException
     */
    public static void dumpDocFile(Indexer indexer, RandomAccessFile DocumentFile, RandomAccessFile ContentsFile) throws IOException
    {
        //Go to where we stopped the previous time
        long weight;
        DocumentFile.seek(DocumentFile.length());
        ContentsFile.seek(ContentsFile.length());
        keyEnc_Offsets=indexer.getkeyEnc_Offsets();
        List<Integer> keyList=new ArrayList<>(keyEnc_Offsets.keySet());
        Collections.sort(keyList);
        HashMap<String,DocumentStructure> docsBatch = indexer.getDocsBatch();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream outCon = new ByteArrayOutputStream();
        for(int key : keyList)
        {
            ByteArrayOutputStream eachDoc = new ByteArrayOutputStream();
            ByteArrayOutputStream eachCon =  new ByteArrayOutputStream();
            String encodedKey= keyEnc_Offsets.get(key).getL();
            Long off = new Long(DocumentFile.length() + output.size());
            offsets.put(key,off);
            keyEnc_Offsets.put(key,new Pair<String,Long>(keyEnc_Offsets.get(key).getL(),off));
            //Saving offset in a hashMap so we can know where is each document in randomaccessfile.
            eachDoc.write(encodedKey.getBytes());
            Long docPointer = new Long(ContentsFile.length() + outCon.size());
            eachDoc.write(ByteBuffer.allocate(8).putLong(docPointer.longValue()).array());
            if(docsBatch.get(encodedKey).getTitle() != null)
            {
                byte[] t = (docsBatch.get(encodedKey).getTitle().getBytes(StandardCharsets.UTF_8));
                Integer s = new Integer(t.length);
                eachCon.write(ByteBuffer.allocate(2).putShort(s.shortValue()).array());
                eachCon.write(t);
            }
            if(docsBatch.get(encodedKey).getAuthorNames() != null)
            {

                ByteArrayOutputStream output1 = new ByteArrayOutputStream();
                for(String s : docsBatch.get(encodedKey).getAuthorNames())
                {
                    output1.write(s.getBytes(StandardCharsets.UTF_8));
                }
                Integer size = new Integer(output1.size());
                eachCon.write(ByteBuffer.allocate(4).putInt(size).array());
                eachCon.write(output1.toByteArray());
            }
            if(docsBatch.get(encodedKey).getAuthorIDs() != null)
            {
                byte[] t = docsBatch.get(encodedKey).getAuthorIDs().toString().getBytes();
                Integer s = new Integer(t.length);
                eachCon.write(ByteBuffer.allocate(4).putInt(s).array());
                eachCon.write(t);
            }
            eachCon.write(ByteBuffer.allocate(2).putShort(docsBatch.get(encodedKey).getYear()).array());
            if(docsBatch.get(encodedKey).getJournalName() != null)
            {
                byte[] t = docsBatch.get(encodedKey).getJournalName().getBytes(StandardCharsets.UTF_8);
                Integer size = new Integer(t.length);
                eachCon.write(ByteBuffer.allocate(2).putShort(size.shortValue()).array());
                eachCon.write(t);
            }
            outCon.write(ByteBuffer.allocate(8).putLong(eachCon.size()).array());
            outCon.write(eachCon.toByteArray());
            //Temporarely saving doc's weight as 0 because we are going to calculate it
            //after finishing the process of indexing.
            eachDoc.write(doubletoByteArray(0.0)); //weight
            eachDoc.write(ByteBuffer.allocate(4).putInt(docsBatch.get(encodedKey).getDocsLength()).array()); //Length
            eachDoc.write(doubletoByteArray(0.0)); //pagerank
            output.write(eachDoc.toByteArray());
        }
        DocumentFile.write(output.toByteArray());
        ContentsFile.write(outCon.toByteArray());
        output.close();
        outCon.close();
        DocumentFile.close();
        ContentsFile.close();
    }

    public void dumpEncodings(Indexer indexer) throws IOException
    {
        BufferedWriter br = new BufferedWriter(new FileWriter(indexer.getIndexDirectory() + __CONFIG__.getEncodings_OffsetsFileName(),true));

        for(int i : keyEnc_Offsets.keySet())
        {

            br.write(i+" "+keyEnc_Offsets.get(i).getL()+" "+keyEnc_Offsets.get(i).getR()+"\n");
        }
        br.close();
    }

    /**
     * Calculates weights for each document.
     * @param indexer
     * @throws IOException
     */
    public void calculateWeights(Indexer indexer) throws IOException
    {
        long et,st;
        //Key denotes doc's ID and value denotes doc's weight
        HashMap<String,Double> t = new HashMap<>();
        RandomAccessFile Docs = new RandomAccessFile(indexer.getIndexDirectory() +__DOCUMENTS_FILENAME__,"rw");
        RandomAccessFile Posts = new RandomAccessFile(indexer.getIndexDirectory() + __POSTINGS_FILENAME__,"rw");
        indexer.load2();
        keyEnc_Offsets= indexer.getkeyEnc_Offsets();
        int allArticles = keyEnc_Offsets.size();
        System.out.print("allArticles " +allArticles+ " " + "\nLoading vocab ");
        et = System.nanoTime();
        indexer.load();
        st = System.nanoTime();
        System.out.println(st-et);
        System.out.print("Calculating weights...");
        et = System.nanoTime();
        for(String s : indexer.getLoadedVocab().keySet())
        {
            //Calculating weights and saving them into a hashmap
            int df = indexer.getLoadedVocab().get(s).getL();
            ByteBuffer b = ByteBuffer.allocate(df*16);
            Posts.seek(indexer.getLoadedVocab().get(s).getR());
            Posts.getChannel().read(b);
            b.position(0);
            for (int i = 0; i < df; i++)
            {
                int key = b.getInt();
                String encodedKey = this.keyEnc_Offsets.get(key).getL();
                int tf = b.getInt(); //this is tf
                double weight = Math.pow((tf * Math.log((allArticles+1)/df))/Math.log(2.0),2);
                if(t.get(encodedKey) != null) weight += t.get(encodedKey);
                t.put(encodedKey,weight);
                b.position(b.position() + 8);
            }
        }
        st = System.nanoTime();
        System.out.println(st-et);
        System.out.print("Writing... ");
        et = System.nanoTime();

        for(Pair<String,Long> s : keyEnc_Offsets.values())
        {
            try {
                Docs.seek(s.getR() + 48);
                Docs.writeDouble(t.get(s.getL()));
            }
            catch(Exception e) { }
        }

        st = System.nanoTime();
        System.out.println(st-et);
        Docs.close();
        Posts.close();
        //Assigning in each doc the weights.
        //Going through each doc and writing weights
    }

    public void meta(Indexer indexer) throws IOException
    {
        Map<String,String> meta = indexer.getMeta();
        File MetaIndex = new File(indexer.getIndexDirectory() + __CONFIG__.getMetaFileName());
        BufferedWriter bw = new BufferedWriter(new FileWriter(MetaIndex));
        for(String k : meta.keySet())
        {
            bw.write(k + " " + meta.get(k) + "\n");
        }
        bw.close();
        calculateWeights(indexer);
    }

    public void print(Indexer indexer) throws IOException {
        RandomAccessFile Docs = new RandomAccessFile(indexer.getIndexDirectory() +__DOCUMENTS_FILENAME__,"rw");
        for(int i = 0; i < 10000; i++) {
            byte[] b = new byte[40];
            Docs.read(b, 0, 40);
            String s = new String(b);

            Docs.seek(Docs.getFilePointer() + 8);
            Double d = Docs.readDouble();
            System.out.println(s +"\n" +d +"\n-----------");
            Docs.seek(Docs.getFilePointer() + 12);
//            Docs.seek(Docs.getFilePointer() + 12);
        }
    }

    public void setID(int id) {
        this.id = id;
    }

    /**
     * Returns if index is partial
     *
     * @return
     */
    public boolean isPartial() {
        return id != 0;
    }

    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }
    public static long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes);
        buffer.flip();//need flip
        return buffer.getLong();
    }

    public static byte[] doubletoByteArray(double value) {
        byte[] bytes = new byte[8];
        ByteBuffer.wrap(bytes).putDouble(value);
        return bytes;
    }
}
