# -*- coding: utf-8 -*-

def valid(line):
    if len(line.split())<=4:
        return False
    if  '|' in line:
        return False
    if '..........' in line:
        return False
    return True

from xml.dom import minidom
xmldoc = minidom.parse('/u/rsimm/Downloads/alignedCorpus_en_fr.xml')
frFile = open('french_train_set_23', 'w')
enFile = open('english_train_set_23', 'w')
bad=0
badfr=0



pairList  = xmldoc.getElementsByTagName('link');
englishList = xmldoc.getElementsByTagName('s1')
frenchList = xmldoc.getElementsByTagName('s2')
print(len(englishList))
print(len(frenchList))
for pair in pairList:
    if pair.getAttribute('type') == "1-1":
        s1=pair.getElementsByTagName('s1')[0]
        s2=pair.getElementsByTagName('s2')[0]


        if(s1 is not None and s1.firstChild is not None) and (s2 is not None and s2.firstChild is not None):
              l_en=str(s1.firstChild.nodeValue)
              l_fr=str(s2.firstChild.nodeValue)
              l_en=l_en.replace('%quot%','\"')
              l_fr=l_fr.replace('%quot%','\"')
              l_en=l_en.replace('%gt%','')
              l_fr=l_fr.replace('%gt%','')



              if valid(l_en) and valid(l_fr):
                enFile.write(l_en)
                enFile.write("\n")
                frFile.write(l_fr)
                frFile.write("\n")




























'''
pairList
englishList = xmldoc.getElementsByTagName('s1')
frenchList = xmldoc.getElementsByTagName('s2')
print(len(englishList))
print(len(frenchList))
for index in range(len(englishList)):
    s1=englishList[index]
    s2=frenchList[index]

    en_p_list=s1.getElementsByTagName('p')
    fr_p_list=s2.getElementsByTagName('p')

    if(s1 is not None and s1.firstChild is not None) and (s2 is not None and s2.firstChild is not None):
        if len(en_p_list)==0 and len(fr_p_list)==0:
          l_en=str(s1.firstChild.nodeValue)
          l_fr=str(s2.firstChild.nodeValue)

          if valid(l_en) and valid(l_fr):
            enFile.write(l_en)
            enFile.write("\n")
            frFile.write(l_fr)
            frFile.write("\n")
        elif len(en_p_list)!=0 and len(fr_p_list)!=0:
            for p_index in range(len(en_p_list)):
                    p_en=en_p_list[p_index]
                    enFile.write(str(p_en.firstChild.nodeValue))
                    enFile.write(" ")
            for p_index in range(len(fr_p_list)):
                    p_fr=fr_p_list[p_index]
                    frFile.write(str(p_fr.firstChild.nodeValue))
                    frFile.write(" ")
            enFile.write("\n")
            frFile.write("\n")
        elif len(en_p_list)!=0 and len(fr_p_list)==0:
            l_fr=str(s2.firstChild.nodeValue)
            print("FRENCH \n"+l_fr)
            frFile.write(l_fr)
            frFile.write("\n")
            for p_index in range(len(en_p_list)):
                p_en=en_p_list[p_index]
                enFile.write(str(p_en.firstChild.nodeValue))
                print(str(p_en.firstChild.nodeValue))
                enFile.write(" ")
            print('---')
            enFile.write("\n")
        elif len(en_p_list)==0 and len(fr_p_list)!=0:
            print("ENGLISH \n"+l_en)
            l_en=str(s1.firstChild.nodeValue)
            enFile.write(l_en)
            enFile.write("\n")
            for p_index in range(len(fr_p_list)):
                    p_fr=fr_p_list[p_index]
                    frFile.write(str(p_fr.firstChild.nodeValue))
                    frFile.write(" ")
                    print(str(p_fr.firstChild.nodeValue))
            print("---")
            frFile.write("\n")


print('complete')




for s1 in englishList:
    p_list=s1.getElementsByTagName('p')
    if(s1 is not None and s1.firstChild is not None):
        if len(p_list)==0:
          l=str(s1.firstChild.nodeValue)
          if valid(l):
            enFile.write(l)
            enFile.write("\n")
          else:
            bad+=1
        else:
            for p in p_list:
                enFile.write(str(p.firstChild.nodeValue))
                enFile.write(" ")
            enFile.write("\n")
print('English complete')

frenchList = xmldoc.getElementsByTagName('s2')
print(len(frenchList))
for s2 in frenchList:
    p_list=s2.getElementsByTagName('p')
    if(s2 is not None and s2.firstChild is not None):
        if len(p_list)==0:
          l=str(s2.firstChild.nodeValue)
          if valid(l):
            frFile.write(l)
            frFile.write("\n")
          else:
            badfr+=1
        else:
            for p in p_list:
                frFile.write(str(p.firstChild.nodeValue))
                frFile.write(" ")
            frFile.write("\n")
            '''
print("English v French")
print(bad)
print(badfr)

#train is 10000
#test is 1700