import java.util.PriorityQueue;

/**
 * Although this class has a history of several years, it is starting from a
 * blank-slate, new and clean implementation as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information and including
 * debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD);
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;

	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out) {

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);

		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);

		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();

	}

	/**
	 * Reads the file being compressed one more time and writes the encoding for
	 * each eight-bit chunk, followed by the encoding for PSEUDO_EOF.
	 * 
	 * @param codings
	 *            The array of bit encodings.
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1)
				break;
			String code = codings[val];
			out.writeBits(code.length(), Integer.parseInt(code, 2));
		}
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code, 2));
	}

	/**
	 * Writes the Huffman trie/tree of the compressed file.
	 * 
	 * @param root
	 *            The head of the Huffman trie/tree used to create encodings.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 * 
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if (root.myLeft == null && root.myRight == null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
			return;
		} else {
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
	}

	/**
	 * Creates encodings for each eight-bit character chunk.
	 * 
	 * @param root
	 *            The head of the Huffman trie/tree used to create encodings.
	 * @return the array of encodings.
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encodings);
		return encodings;
	}

	/**
	 * Helper method for makeCodingsFromTree. Recursively creates the path to
	 * each leaf.
	 * 
	 * @param root
	 *            The head of the Huffman trie/tree used to create encodings.
	 * @param path
	 *            The String of the path to a given leaf.
	 * @param encodings
	 *            The array of bit encodings.
	 */
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if (root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path;
			if (myDebugLevel >= DEBUG_HIGH) {
				System.out.printf("encoding for %d is %s\n", root.myValue, path);
			}
			return;
		} else {
			codingHelper(root.myLeft, path + "0", encodings);
			codingHelper(root.myRight, path + "1", encodings);
		}
	}

	/**
	 * Creates a Huffman trie/tree to be used to create encodings.
	 * 
	 * @param counts
	 *            The frequency of every eight-bit character/chunk in the file
	 *            being compressed.
	 * @return the head of the Huffman trie/tree.
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for (int i = 0; i < counts.length; i++) {
			if (counts[i] > 0)
				pq.add(new HuffNode(i, counts[i], null, null));
		}
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}
		return pq.remove();
	}

	/**
	 * Determines the frequency of every eight-bit character/chunk in the file
	 * being compressed.
	 * 
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @return an array containing the frequency of each bit.
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE + 1];
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1)
				break;
			freq[val]++;
		}
		freq[PSEUDO_EOF] = 1;
		return freq;
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out) {

		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("Illegal header starts with " + bits);
		}

		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();

	}

	/**
	 * Reads the tree used to decompress.
	 * 
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @return the properly formatted tree
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1)
			throw new HuffException("Could not read bit, value = -1");
		if (bit == 0) {
			return new HuffNode(0, 0, readTreeHeader(in), readTreeHeader(in));
		} else {
			return new HuffNode(in.readBits(BITS_PER_WORD + 1), 0, null, null);
		}
	}

	/**
	 * Reads the bits from the compressed file and uses them to traverse
	 * root-to-leaf paths, writing leaf values to the output file. Stops when
	 * PSEUDO_EOF is found
	 * 
	 * @param root
	 *            The head of the tree used to decompress.
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO.EOF");
			} else {
				if (bits == 0)
					current = current.myLeft;
				else
					current = current.myRight;

				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF)
						break;
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
	}

}