#
# Copyright 2012 Clayton Smith
#
# This file is part of Ottawa Bus Follower.
#
# Ottawa Bus Follower is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License as
# published by the Free Software Foundation; either version 3, or (at
# your option) any later version.
#
# Ottawa Bus Follower is distributed in the hope that it will be
# useful, but WITHOUT ANY WARRANTY; without even the implied warranty
# of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with Ottawa Bus Follower; see the file COPYING.  If not, see
# <http://www.gnu.org/licenses/>.
#

# Run this script from the root of the root of the project

import os

path = os.getcwd() + '/'

for filename in os.listdir('svg'):
    if filename.endswith('.svg'):
        fileWithDir = path + 'svg/' + filename
        os.system('inkscape -e ' + path + 'res/drawable-xhdpi/' + filename[0:-4] + '.png -d 320 ' + fileWithDir)
        os.system('inkscape -e ' + path + 'res/drawable-hdpi/' + filename[0:-4] + '.png -d 240 ' + fileWithDir)
        os.system('inkscape -e ' + path + 'res/drawable-mdpi/' + filename[0:-4] + '.png -d 160 ' + fileWithDir)
        os.system('inkscape -e ' + path + 'res/drawable-ldpi/' + filename[0:-4] + '.png -d 120 ' + fileWithDir)
    if filename == 'launcher_icon.svg':
        os.system('inkscape -e ' + path + 'google-play-icon.png -h 512 -w 512 ' + fileWithDir)
