/**
 * user.ts の単体テスト。
 *
 * 対象: fetchUserProfile / updateProfile / fetchUserPosts / followUser / unfollowUser /
 *      fetchFollowing / fetchFollowers / searchUsers / uploadAvatar
 *
 * 技法: 同値分割 + デシジョンテーブル (重複ユーザー名 409 等)
 */
import { http, HttpResponse } from 'msw'
import { server } from '../test/server'
import {
  fetchFollowers,
  fetchFollowing,
  fetchUserPosts,
  fetchUserProfile,
  followUser,
  searchUsers,
  unfollowUser,
  updateProfile,
  uploadAvatar,
  type UserProfile,
  type UserSummary,
} from './user'

const sampleProfile: UserProfile = {
  id: 1,
  username: 'alice',
  displayName: 'アリス',
  avatarUrl: null,
  bio: null,
  followingCount: 0,
  followerCount: 0,
  isFollowing: false,
  isOwnProfile: true,
}

const sampleSummary: UserSummary = {
  id: 2,
  username: 'bob',
  displayName: 'ボブ',
  avatarUrl: null,
  isFollowing: false,
}

describe('user API', () => {
  describe('fetchUserProfile', () => {
    it('GET /users/{username} を呼んでプロフィールを返す', async () => {
      let capturedUsername: string | null = null
      server.use(
        http.get('/api/users/:username', ({ params }) => {
          capturedUsername = String(params.username)
          return HttpResponse.json(sampleProfile)
        })
      )

      const result = await fetchUserProfile('alice')

      expect(result).toEqual(sampleProfile)
      expect(capturedUsername).toBe('alice')
    })
  })

  describe('updateProfile', () => {
    it('PUT /users/me に updated body を送る', async () => {
      let capturedBody: unknown = null
      server.use(
        http.put('/api/users/me', async ({ request }) => {
          capturedBody = await request.json()
          return HttpResponse.json(sampleProfile)
        })
      )

      await updateProfile({ username: 'alice', displayName: 'アリス', bio: 'hi' })

      expect(capturedBody).toEqual({ username: 'alice', displayName: 'アリス', bio: 'hi' })
    })

    it('409 (ユーザー名重複) は例外を伝播する', async () => {
      server.use(
        http.put('/api/users/me', () =>
          HttpResponse.json({ message: 'このユーザー名は既に使用されています' }, { status: 409 })
        )
      )

      await expect(
        updateProfile({ username: 'taken', displayName: 'x', bio: '' })
      ).rejects.toThrow()
    })
  })

  describe('fetchUserPosts', () => {
    it('GET /users/{username}/posts を呼ぶ', async () => {
      server.use(http.get('/api/users/:username/posts', () => HttpResponse.json([])))

      const result = await fetchUserPosts('alice')

      expect(result).toEqual([])
    })
  })

  describe('followUser / unfollowUser', () => {
    it('followUser: POST /users/{userId}/follow', async () => {
      let calledId: string | null = null
      server.use(
        http.post('/api/users/:userId/follow', ({ params }) => {
          calledId = String(params.userId)
          return new HttpResponse(null, { status: 204 })
        })
      )

      await followUser(2)

      expect(calledId).toBe('2')
    })

    it('unfollowUser: DELETE /users/{userId}/follow', async () => {
      let calledId: string | null = null
      server.use(
        http.delete('/api/users/:userId/follow', ({ params }) => {
          calledId = String(params.userId)
          return new HttpResponse(null, { status: 204 })
        })
      )

      await unfollowUser(2)

      expect(calledId).toBe('2')
    })
  })

  describe('fetchFollowing / fetchFollowers', () => {
    it('fetchFollowing: GET /users/{username}/following', async () => {
      server.use(
        http.get('/api/users/:username/following', () => HttpResponse.json([sampleSummary]))
      )

      const result = await fetchFollowing('alice')

      expect(result).toEqual([sampleSummary])
    })

    it('fetchFollowers: GET /users/{username}/followers', async () => {
      server.use(
        http.get('/api/users/:username/followers', () => HttpResponse.json([sampleSummary]))
      )

      const result = await fetchFollowers('alice')

      expect(result).toEqual([sampleSummary])
    })
  })

  describe('searchUsers', () => {
    it('q クエリを付けて GET /users/search を呼ぶ', async () => {
      let capturedQ: string | null = null
      server.use(
        http.get('/api/users/search', ({ request }) => {
          capturedQ = new URL(request.url).searchParams.get('q')
          return HttpResponse.json([sampleSummary])
        })
      )

      const result = await searchUsers('bo')

      expect(capturedQ).toBe('bo')
      expect(result).toEqual([sampleSummary])
    })
  })

  describe('uploadAvatar', () => {
    it('POST /users/me/avatar に file フィールドを multipart で送る', async () => {
      let capturedFormData: FormData | null = null
      server.use(
        http.post('/api/users/me/avatar', async ({ request }) => {
          capturedFormData = await request.formData()
          return HttpResponse.json(sampleProfile)
        })
      )
      const file = new File(['dummy'], 'a.png', { type: 'image/png' })

      await uploadAvatar(file)

      // MSW は File を別 realm で生成するため instanceof でなく shape で検証する
      const sentFile = capturedFormData!.get('file') as File
      expect(sentFile).toBeTruthy()
      expect(sentFile.size).toBeGreaterThan(0)
    })
  })
})
