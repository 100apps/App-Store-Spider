
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.BitSet;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class GetIds {
	private String date = "20160523";
	private BitSet existsFiles = new BitSet(10);
	static Logger log = Logger.getLogger("AppStore-GetIds");
	private Pattern pattern = Pattern.compile("/app/(.*?)/id(.*?)\\?");
	private int total = 0;

	private byte[] get(String url) {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			URLConnection con = new URL(url).openConnection();
			con.setRequestProperty("User-Agent",
					"iTunes/12.4.1 (Windows; Microsoft Windows 10.0 x64 Enterprise Edition (Build 9200); x64) AppleWebKit/7601.6016.1000.1");
			InputStream is = con.getInputStream();
			byte[] byteChunk = new byte[4096];
			int n;
			while ((n = is.read(byteChunk)) > 0) {
				baos.write(byteChunk, 0, n);
			}
			return baos.toByteArray();
		} catch (Exception e) {
			log.warning(url + "\n" + e.getMessage());
			return null;
		}
	}

	private void loadExists() {
		File f = new File(this.date);
		if (!f.exists()) {
			f.mkdirs();
			return;
		}
		for (String fn : f.list()) {
			int i = 0;
			try {
				i = Integer.parseInt(fn);
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (i > 0) {
				existsFiles.set(i);
			}
		}
		log.info("存在的文件：" + existsFiles.cardinality());
	}

	private void go(int subfix) {
		if (existsFiles.get(subfix)) {
			log.info(subfix + " exists, ignore.");
			return;
		}
		existsFiles.set(subfix);

		boolean hasMore = true;
		int page = 1;
		HashMap<Integer, Long> result = new HashMap<>();
		while (hasMore) {
			byte[] content = get(
					"http://sitemaps.itunes.apple.com/itunes_" + subfix + "_" + page + "_" + date + ".xml.gz");
			if (content == null) {
				hasMore = false;
				log.info(subfix + " ends in " + page);
			} else {
				try (GZIPInputStream gin = new GZIPInputStream(new ByteArrayInputStream(content));
						ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
					byte[] byteChunk = new byte[4096];
					int n;
					while ((n = gin.read(byteChunk)) > 0) {
						baos.write(byteChunk, 0, n);
					}
					Matcher m = pattern.matcher(new String(baos.toByteArray(), "UTF-8"));
					while (m.find()) {
						long id = 0;
						try {
							id = Long.parseLong(m.group(2));
						} catch (Exception e) {
							e.printStackTrace();
						}
						if (id > 0) {
							result.put((int) (id / 1000), id);
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				log.info(subfix + "\t" + content.length);
				page++;
			}
		}
		StringBuilder sb = new StringBuilder();
		for (Long k : result.values()) {
			sb.append(k).append("\n");
		}
		try {
			Files.write(Paths.get(this.date + File.separator + subfix), sb.toString().getBytes("UTF-8"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		total += result.size();
		log.info("finish <" + subfix + "> with total :\t" + total);
		result.clear();
	}

	public static void main(String[] args) {
		System.err.println(
				"java GetIds [nThreads] [date]\n if you get all app id, use \n https://itunes.apple.com/app/id<id> to get app info\n for example: https://itunes.apple.com/app/id1053336048\n");
		GetIds get = new GetIds();
		int nThreads = 10;

		if (args.length > 0) {
			nThreads = Integer.parseInt(args[0]);
		}
		if (args.length > 1) {
			get.date = args[1];
		}

		get.loadExists();

		ExecutorService exe = Executors.newFixedThreadPool(nThreads);
		for (int i = 1; i <= 1000; i++) {
			final int j = i;
			exe.execute(new Runnable() {
				@Override
				public void run() {
					get.go(j);
				}
			});
		}
		exe.shutdown();

		try {
			exe.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

}
