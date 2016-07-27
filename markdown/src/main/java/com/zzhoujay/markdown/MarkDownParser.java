package com.zzhoujay.markdown;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import com.zzhoujay.markdown.parser.Line;
import com.zzhoujay.markdown.parser.LineQueue;
import com.zzhoujay.markdown.parser.QueueConsumer;
import com.zzhoujay.markdown.parser.StyleBuilder;
import com.zzhoujay.markdown.parser.Tag;
import com.zzhoujay.markdown.parser.TagHandler;
import com.zzhoujay.markdown.parser.TagHandlerImpl;
import com.zzhoujay.markdown.style.ScaleHeightSpan;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import android.util.*;

/**
 * Created by zhou on 16-6-25.
 */
class MarkDownParser {


    private BufferedReader reader;
    private StyleBuilder styleBuilder;
    private TagHandler tagHandler;

    MarkDownParser(BufferedReader reader, StyleBuilder styleBuilder) {
        this.reader = reader;
        this.styleBuilder = styleBuilder;
        tagHandler = new TagHandlerImpl(styleBuilder);
    }

    MarkDownParser(InputStream inputStream, StyleBuilder styleBuilder) {
        this(new BufferedReader(new InputStreamReader(inputStream)), styleBuilder);
    }

    MarkDownParser(String text, StyleBuilder styleBuilder) {
        this(new BufferedReader(new StringReader(text)), styleBuilder);
    }


    public Spannable parser() throws IOException {
        LineQueue queue = collect();
        return analytic(queue);
    }

    private LineQueue collect() throws IOException {
        String line;
        Line root = null;
        LineQueue queue = null;
        while ((line = reader.readLine()) != null) {
            if (!(tagHandler.imageId(line) || tagHandler.linkId(line))) {
                Line l = new Line(line);
                if (root == null) {
                    root = l;
                    queue = new LineQueue(root);
                } else {
                    queue.append(l);
                }
            }
        }
        return queue;
    }

    private Spannable analytic(final LineQueue queue) {
        tagHandler.setQueueProvider(new QueueConsumer.QueueProvider() {
            @Override
            public LineQueue getQueue() {
                return queue;
            }
        });
        removeCurrBlankLine(queue);
        boolean need_next;
        boolean notBlock;
        do {
            notBlock = false;
            need_next = true;
            Line next = queue.nextLine();
            Line curr = queue.currLine();

            if (queue.prevLine() != null && (queue.prevLine().getType() == Line.LINE_TYPE_OL || queue.prevLine().getType() == Line.LINE_TYPE_UL)
                    && (tagHandler.find(Tag.UL, queue.currLine()) || tagHandler.find(Tag.OL, queue.currLine()))) {
                notBlock = true;
            }

            if (!notBlock && (tagHandler.codeBlock1(queue.currLine())||tagHandler.codeBlock2(queue.currLine()))) {
                continue;
            }

//            if (tagHandler.find(Tag.H1_2, next)) {
//                queue.currLine().setType(Line.LINE_TYPE_H1);
//                queue.currLine().setStyle(SpannableStringBuilder.valueOf(queue.currLine().getSource()));
//                tagHandler.inline(queue.currLine());
//                queue.currLine().setStyle(styleBuilder.h1(queue.currLine().getStyle()));
//                queue.removeNextLine();
//                removeNextBlankLine(queue);
//                continue;
//            }
//
//            if (tagHandler.find(Tag.H2_2, next)) {
//                queue.currLine().setType(Line.LINE_TYPE_H2);
//                queue.currLine().setStyle(SpannableStringBuilder.valueOf(queue.currLine().getSource()));
//                tagHandler.inline(queue.currLine());
//                queue.currLine().setStyle(styleBuilder.h2(queue.currLine().getStyle()));
//                queue.removeNextLine();
//                removeNextBlankLine(queue);
//                continue;
//            }

            boolean isNewLine = tagHandler.find(Tag.NEW_LINE, queue.currLine()) || tagHandler.find(Tag.GAP, queue.currLine());
            if (isNewLine) {
                removeNextBlankLine(queue);
            }else{
				while(queue.nextLine()!=null&&!removeNextBlankLine(queue)){
					if (tagHandler.find(Tag.CODE_BLOCK_1, queue.nextLine()) || tagHandler.find(Tag.CODE_BLOCK_2, queue.nextLine()) ||
                        tagHandler.find(Tag.GAP, queue.nextLine()) || tagHandler.find(Tag.UL, queue.nextLine()) ||
                        tagHandler.find(Tag.OL, queue.nextLine()) || tagHandler.find(Tag.H, queue.nextLine())) {
						break;
					}
					int nextQuotaCount = tagHandler.findCount(Tag.QUOTA, queue.nextLine(), 1);
					int currQuotaCount = tagHandler.findCount(Tag.QUOTA, queue.currLine(), 1);
					if (nextQuotaCount > 0 && nextQuotaCount > currQuotaCount) {
						break;
					} else {
						String r = queue.nextLine().getSource();
						if (nextQuotaCount > 0) {
							r = r.replaceFirst("^\\s{0,3}(>\\s+){" + nextQuotaCount + "}", "");
						}
						if(currQuotaCount==nextQuotaCount){
							if (tagHandler.find(Tag.H1_2, r)) {
								queue.currLine().setSource("# "+queue.currLine().getSource());
//								queue.currLine().setType(Line.LINE_TYPE_H1);
//								queue.currLine().setStyle(SpannableStringBuilder.valueOf(queue.currLine().getSource()));
//								tagHandler.inline(queue.currLine());
//								queue.currLine().setStyle(styleBuilder.h1(queue.currLine().getStyle()));
								queue.removeNextLine();
								break;
							}
							if (tagHandler.find(Tag.H2_2, r)) {
								queue.currLine().setSource("## "+queue.currLine().getSource());
//								queue.currLine().setType(Line.LINE_TYPE_H2);
//								queue.currLine().setStyle(SpannableStringBuilder.valueOf(queue.currLine().getSource()));
//								tagHandler.inline(queue.currLine());
//								queue.currLine().setStyle(styleBuilder.h2(queue.currLine().getStyle()));
								queue.removeNextLine();
								break;
							}
						}
						if (tagHandler.find(Tag.UL, r) || tagHandler.find(Tag.OL, r) || tagHandler.find(Tag.H, r)) {
							break;
						}else  {
							queue.currLine().setSource(curr.getSource() + ' ' + r);
							queue.removeNextLine();
						}
					}
				}
				removeNextBlankLine(queue);
			}
            
            if (tagHandler.gap(queue.currLine()) || tagHandler.quota(queue.currLine()) || tagHandler.ol(queue.currLine()) ||
                    tagHandler.ul(queue.currLine()) || tagHandler.h(queue.currLine())) {
                continue;
            }
            curr.setStyle(SpannableStringBuilder.valueOf(queue.currLine().getSource()));
            tagHandler.inline(queue.currLine());
        } while (!need_next || queue.next());
        return merge(queue);
    }

    private Spannable merge(LineQueue queue) {
        queue.reset();
        SpannableStringBuilder builder = new SpannableStringBuilder();
        do {
            Line curr = queue.currLine();
            Line prev = queue.prevLine();
            Line next = queue.nextLine();
            builder.append(curr.getStyle()).append('\n');
            switch (curr.getType()) {
                case Line.LINE_TYPE_QUOTA:
                    if(next!=null&&next.getType()!=Line.LINE_TYPE_QUOTA){
                        builder.append('\n');
                    }
                    break;
                case Line.LINE_TYPE_UL:
                    if (next != null && next.getType() == Line.LINE_TYPE_UL)
                        builder.append(listMarginBottom());
                    builder.append('\n');
                    break;
                case Line.LINE_TYPE_OL:
                    if (next != null && next.getType() == Line.LINE_TYPE_OL) {
                        builder.append(listMarginBottom());
                    }
                    builder.append('\n');
                    break;
				case Line.LINE_TYPE_CODE_BLOCK_1:
				case Line.LINE_TYPE_CODE_BLOCK_2:
                case Line.LINE_TYPE_H3:
                case Line.LINE_TYPE_H4:
                case Line.LINE_TYPE_H5:
                case Line.LINE_TYPE_H6:
                case Line.LINE_TYPE_H1:
                case Line.LINE_TYPE_H2:
                case Line.LINE_TYPE_GAP:
                case Line.LINE_NORMAL:
                    builder.append('\n');
                    break;
            }
        } while (queue.next());
        return builder;
    }
	
	private boolean removeNextBlankLine(LineQueue queue){
		boolean flag=false;
		while(queue.nextLine()!=null){
			if(tagHandler.find(Tag.BLANK,queue.nextLine())){
				queue.removeNextLine();
				flag=true;
			}else{
				break;
			}
		}
		return flag;
	}
	
	private boolean removeCurrBlankLine(LineQueue queue){
		boolean flag=false;
		while(queue.currLine()!=null){
			if(tagHandler.find(Tag.BLANK,queue.currLine())){
				queue.removeCurrLine();
				flag=true;
			}else{
				break;
			}
		}
		return flag;
	}	
	

//    private boolean removeBlankLine(LineQueue queue) {
//        return removeBlankLine(queue, false);
//    }
//
//    private boolean removeBlankLine(LineQueue queue, boolean curr) {
//        boolean flag = false;
//        Line m = curr ? queue.prevLine() : queue.currLine();
//        if (!curr) {
//            queue.next();
//        }
//        do {
//            if (!tagHandler.find(Tag.BLANK, queue.currLine()) || queue.currLine() == m) {
//                break;
//            }
//            flag = true;
//        } while (queue.removeCurrLine() != null);
//        if (!curr) {
//            if (queue.currLine() != m) {
//                queue.prev();
//            }
//        }
//        return flag;
//    }


    private SpannableString listMarginBottom() {
        SpannableString ss = new SpannableString(" ");
        ss.setSpan(new ScaleHeightSpan(0.4f), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }

}
