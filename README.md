# Information-Retrieval-System

This is a Group Project from our university. It is build for the huge collection of pangaia competition ( 110GB ) .

Basically what it does is, it runs through a data base ( for our examination pangaia's database was used ) with articles and for each article extracts the fields one-by-one and applies stemming or/and stopwords in each word ( depending of the config file ). Because the data base is huge, we cannot load everything in memory so we make partial indices, the number of indices is implemented again in config file. In each partial index there are two files, a vocabulary and a postings file there is also a documents file and a content file but these are not created for each partial index, they are created once in the beginning. 
-Vocabulary file contains words, df and pointer to postings file.
-Postings file contains document id ( in which document the specific word is found ), term frequency in the specific document and a pointer to the documents file, so basically it connects vocabulary with documents file.
-Documents file contains the haskey of the document, a pointer to the contents file, doc's weight, doc's length, and doc's pagerank score.
-Contents file contains all the variable fields of a document, for example the title, paper abstract etc.

After we created all the partial indices we start merging them to create the final index. In the merging process we just compare the two partial indices and append in our new files. For example, if we are merging files 1 and 2 a file 12 will appear containing the updated vocabulary and postings file. 

After we finish with merge we go through our index one more time to calculate the weights of each doc ( we need this for our evaluation systems ).

Things above are implemented by me.
I also implemented the pagerank score calculation.

Times that correspond to the project are:
  -Total time for indexing process: 4 hours and 50 minutes
  -Reading time: 150 minutes
  -stem/stop: 112 minutes
  -Merge Time: 63 minutes
  -All info dumping Time: 22 minutes

Sizes of files:
  -Vocabulary: 420MB
  -Postings File: 53.1 GB
  -Documents File + Contents File = 13.8 GB

Apostolos Mavrogiannakis & Haralampos Varsamis, 2019
