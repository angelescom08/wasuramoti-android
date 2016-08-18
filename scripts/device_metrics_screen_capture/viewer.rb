#!/usr/bin/env ruby

require 'webrick'

LISTEN_PORT = 2973
RESULT_DIR = "result"

def id_list()
  Dir.glob(File.join(RESULT_DIR,"*.png")).sort.map{|x|
    File.basename(x,".png")
  }
end

ID_LIST = id_list

def root(req,res)
  html="<!DOCTYPE html><html><head><title>Emulator Screenshot</title></head><body>"
  ID_LIST.each{|id|
    html += "<a href='/capture?id=#{id}'>#{id}</a><br>"
  }
  html+="</body></html>"
end

def capture(req,res)
  id = req.query["id"]

  id_index = ID_LIST.index(id)
  prev_id = if id_index > 0 then ID_LIST[id_index-1] else nil end
  prev_href = if prev_id then "<a href='/capture?id=#{prev_id}'>Prev</a>" else "Prev" end
  next_id = ID_LIST[id_index+1]
  next_href = if next_id then "<a href='/capture?id=#{next_id}'>Next</a>" else "Next" end
  info = File.read(File.join(RESULT_DIR,"#{id}.info")).sub(/mCurConfiguration=/,'')

  %Q(<!DOCTYPE html><html><head><title>#{id}</title></head><body>
  [#{prev_href}] [#{next_href}]
  <hr>
  #{info}
  <hr>
  <img src='/img/#{id}.png'>
  </body></html>  
  )
  
end

ROUTING_TABLE = {
  '/' => :root,
  '/capture' => :capture
}

server = WEBrick::HTTPServer.new(Port:LISTEN_PORT)
server.mount_proc('/') do |req,res|
  route = ROUTING_TABLE[req.path_info]
  if route then
    res.body = send(route,req,res)
    res.content_type = "text/html; charset=utf-8"
  else
    res.body = "404 Not Found"
    res.status = 404
  end
end
server.mount('/img', WEBrick::HTTPServlet::FileHandler, RESULT_DIR)

trap 'INT' do server.shutdown end
server.start
